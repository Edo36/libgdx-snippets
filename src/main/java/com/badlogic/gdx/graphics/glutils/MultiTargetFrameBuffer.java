package com.badlogic.gdx.graphics.glutils;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.*;

import static com.badlogic.gdx.Gdx.*;
import static com.badlogic.gdx.graphics.GL30.*;
import static com.badlogic.gdx.graphics.Texture.*;

/**
 * An extension to {@link FrameBuffer} with multiple color attachments. Can be used as
 * multi-render-target in deferred rendering (G-buffer).
 * <p>
 * Uses alternate depth/stencil buffer formats to allow for GL_DEPTH24_STENCIL8.
 */
public class MultiTargetFrameBuffer extends GLFrameBuffer<Texture> {

	private Texture[] colorTextures;
	private int depthBufferHandle;
	private int depthStencilBufferHandle;

	private static IntBuffer attachmentIds;
	private static final FloatBuffer tmpColors = BufferUtils.newFloatBuffer(4);

	/**
	 * Creates a new MRT FrameBuffer with float color buffer format and the given dimensions.
	 */
	public MultiTargetFrameBuffer(int numColorBuffers, int width, int height, boolean hasDepth, boolean hasStencil) {
		this(null, numColorBuffers, width, height, hasDepth, hasStencil);
	}

	/**
	 * Creates a new MRT FrameBuffer with the given format and dimensions.
	 */
	public MultiTargetFrameBuffer(Pixmap.Format format, int numColorBuffers, int width, int height,
								  boolean hasDepth, boolean hasStencil) {
		super(format, width, height, false, false);
		build(numColorBuffers, hasDepth, hasStencil);
	}

	/**
	 * Completes the MRT FrameBuffer by attaching the additional color buffers, plus optional depth and stencil buffers.
	 * This is done after the initial creation in {@link GLFrameBuffer#build()}, so glCheckFramebufferStatus() is
	 * called again.
	 */
	private void build(int numColorBuffers, boolean hasDepth, boolean hasStencil) {

		bind();

		// create and attach additional color buffers

		colorTextures = new Texture[numColorBuffers];
		colorTextures[0] = colorTexture;

		for (int i = 1; i < numColorBuffers; i++) {
			colorTextures[i] = createColorTexture();
			gl30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D,
					colorTextures[i].getTextureObjectHandle(), 0);
		}

		synchronized (MultiTargetFrameBuffer.class) {

			if (attachmentIds == null || numColorBuffers > attachmentIds.capacity()) {
				attachmentIds = BufferUtils.newIntBuffer(numColorBuffers);
				for (int i = 0; i < numColorBuffers; i++) {
					attachmentIds.put(i, GL_COLOR_ATTACHMENT0 + i);
				}
			}

			gl30.glDrawBuffers(numColorBuffers, attachmentIds);
		}

		// depth texture, or depth/stencil render target

		if (hasStencil) {

			depthStencilBufferHandle = gl30.glGenRenderbuffer();

			gl30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilBufferHandle);
			gl30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);

			gl30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

			gl30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
					GL30.GL_RENDERBUFFER, depthStencilBufferHandle);

		} else if (hasDepth) {

			depthBufferHandle = gl30.glGenTexture();

			gl30.glBindTexture(GL30.GL_TEXTURE_2D, depthBufferHandle);
			gl30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, width, height, 0,
					GL30.GL_DEPTH_COMPONENT, GL30.GL_FLOAT, null);

			gl30.glBindTexture(GL30.GL_TEXTURE_2D, 0);

			gl30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
					GL30.GL_TEXTURE_2D, depthBufferHandle, 0);
		}

		// check status again

		int result = gl30.glCheckFramebufferStatus(GL_FRAMEBUFFER);

		unbind();

		if (result != GL_FRAMEBUFFER_COMPLETE) {
			dispose();
			throw new IllegalStateException("frame buffer couldn't be constructed: error " + result);
		}
	}

	@Override
	protected Texture createColorTexture() {
		Texture result;

		if (format != null) {
			result = new Texture(width, height, format);
		} else {
			ColorBufferTextureData data = new ColorBufferTextureData(width, height);
			result = new Texture(data);
		}

		result.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		result.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);

		return result;
	}

	@Override
	protected void disposeColorTexture(Texture colorTexture) {
		for (int i = 0; i < colorTextures.length; i++) {
			colorTextures[i].dispose();
		}

		if (depthBufferHandle != 0) {
			gl30.glDeleteTexture(depthBufferHandle);
		}

		if (depthStencilBufferHandle != 0) {
			gl30.glDeleteRenderbuffer(depthStencilBufferHandle);
		}
	}

	public Texture getColorBufferTexture(int index) {
		return colorTextures[index];
	}

	public void clampToBorder(int index, Color color) {
		int handle = colorTextures[index].getTextureObjectHandle();
		gl30.glBindTexture(GL30.GL_TEXTURE_2D, handle);

		gl30.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL33Ext.GL_CLAMP_TO_BORDER);
		gl30.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL33Ext.GL_CLAMP_TO_BORDER);

		synchronized (tmpColors) {
			tmpColors.clear();
			tmpColors.put(color.r);
			tmpColors.put(color.g);
			tmpColors.put(color.b);
			tmpColors.put(color.a);
			tmpColors.flip();

			gl30.glTexParameterfv(GL30.GL_TEXTURE_2D, GL33Ext.GL_TEXTURE_BORDER_COLOR, tmpColors);
		}

		gl30.glBindTexture(GL30.GL_TEXTURE_2D, 0);
	}

	public void clearColorBuffer(Color color, int... indices) {
		synchronized (tmpColors) {
			tmpColors.clear();
			tmpColors.put(color.r);
			tmpColors.put(color.g);
			tmpColors.put(color.b);
			tmpColors.put(color.a);
			tmpColors.flip();

			for (int index : indices) {
				gl30.glClearBufferfv(GL30.GL_COLOR, index, tmpColors);
			}
		}
	}

	public void clearDepthBuffer(float depth) {
		gl30.glClearBufferfi(GL30.GL_DEPTH, 0, depth, 0);
	}

	public void clearStencilBuffer(int value) {
		gl30.glClearStencil(value);
	}

	private class ColorBufferTextureData implements TextureData {

		private int width;
		private int height;

		ColorBufferTextureData(int width, int height) {
			this.width = width;
			this.height = height;
		}

		@Override
		public TextureDataType getType() {
			return TextureDataType.Custom;
		}

		@Override
		public boolean isPrepared() {
			return true;
		}

		@Override
		public void prepare() {
		}

		@Override
		public Pixmap consumePixmap() {
			return null;
		}

		@Override
		public boolean disposePixmap() {
			return false;
		}

		@Override
		public void consumeCustomData(int target) {
			gl30.glTexImage2D(target, 0, GL_RGB32F, width, height, 0, GL_RGB, GL_FLOAT, null);
		}

		@Override
		public int getWidth() {
			return width;
		}

		@Override
		public int getHeight() {
			return height;
		}

		@Override
		public Pixmap.Format getFormat() {
			return null;
		}

		@Override
		public boolean useMipMaps() {
			return false;
		}

		@Override
		public boolean isManaged() {
			return true;
		}
	}
}
