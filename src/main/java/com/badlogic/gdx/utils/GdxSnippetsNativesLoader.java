package com.badlogic.gdx.utils;

public class GdxSnippetsNativesLoader {

	/**
	 * Loads the native library, and initializes the flextGL OpenGL bindings.
	 */
	public static synchronized void load(boolean setupGLBindings) {

		try {

			// customized library name: 32/64 bit shared library on OS X

			SharedLibraryLoader loader = new SharedLibraryLoader() {
				@Override
				public String mapLibraryName(String libraryName) {
					if (isMac) {
						return "lib" + libraryName + ".dylib";
					}
					return super.mapLibraryName(libraryName);
				}
			};

			// load native library

			loader.load("gdx-snippets");

			// initialize flextGL bindings

			if (setupGLBindings) {
				flextInit();
			}

		} catch (Exception e) {
			System.err.println(e.getMessage());
		}

	}

	// @off

	/*JNI
		#include "flextGL.h"
	*/

	private static native void flextInit(); /*
		flextInit();
	*/

}
