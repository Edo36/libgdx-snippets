package com.badlogic.gdx.graphics;

import com.badlogic.gdx.Gdx;

import java.nio.LongBuffer;

/**
 * Extension to Gdx.gl30 which adds functions and constants not available to OpenGL ES, and
 * therefore not exposed through the libGDX interface.
 */
public final class GL33Ext {

	public static final int GL_TEXTURE_BORDER_COLOR = 0x1004;

	public static final int GL_POINT = 0x1B00;
	public static final int GL_LINE = 0x1B01;
	public static final int GL_FILL = 0x1B02;

	public static final int GL_CLAMP_TO_BORDER = 0x812D;

	public static final int GL_INTERNALFORMAT_SUPPORTED = 0x826F;
	public static final int GL_INTERNALFORMAT_PREFERRED = 0x8270;

	public static void glBlendEquationi(int buffer, int mode) {

		if (!Gdx.graphics.supportsExtension("GL_ARB_draw_buffers_blend")) {
			System.err.println("Extension ARB_draw_buffers_blend not supported!");
		}

		nglBlendEquationi(buffer, mode);
	}

	public static void glGetInternalFormativ(int target, int internalformat, int pname, LongBuffer params) {

		if (!Gdx.graphics.supportsExtension("GL_ARB_internalformat_query2")) {
			System.err.println("Extension ARB_internalformat_query2 not supported!");
		}

		nglGetInternalFormati64v(target, internalformat, pname, params.capacity(), params);
	}

	// @off

	/*JNI
		#include "flextGL.h"
	*/

	public static native void glBindFragDataLocation(int program, int colorNumber, String name); /*
		glBindFragDataLocation(program, colorNumber, name);
	*/

	private static native void nglBlendEquationi(int buffer, int mode); /*
		if (FLEXT_ARB_draw_buffers_blend) {
			glpfBlendEquationiARB(buffer, mode);
		}
	*/

	private static native void nglGetInternalFormati64v(int target, int internalformat,
														int pname, int bufSize, LongBuffer params); /*
		if (FLEXT_ARB_internalformat_query2) {
			glGetInternalformati64v(target, internalformat, pname, bufSize, params);
		}
	*/

	public static native void glPolygonMode(int face, int mode); /*
		glPolygonMode(face, mode);
	*/

}
