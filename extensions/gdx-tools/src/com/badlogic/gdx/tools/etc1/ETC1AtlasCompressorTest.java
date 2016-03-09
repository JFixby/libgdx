/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.tools.etc1;

import java.io.File;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.utils.Array;

public class ETC1AtlasCompressorTest implements ApplicationListener {

	private String regularAtlasPath;
	private FileHandle regularAtlasPathFile;

	private String etc1AtlasPath;
	private FileHandle etc1AtlasPathFile;

	public ETC1AtlasCompressorTest (String regularAtlasPath, String etc1AtlasPath) {
		this.regularAtlasPath = regularAtlasPath;
		this.regularAtlasPathFile = new FileHandle(regularAtlasPath);

		this.etc1AtlasPath = etc1AtlasPath;
		this.etc1AtlasPathFile = new FileHandle(etc1AtlasPath);
	}

	public static void main (String[] args) throws Exception {

		String applicationHomePathString = System.getProperty("user.dir");

		FileHandle homeFolder = new FileHandle(new File(applicationHomePathString));
		FileHandle spritesFolder = homeFolder.child("sprites");
		FileHandle regularAtlasFolder = homeFolder.child("atlas");
		FileHandle etc1AtlasFolder = homeFolder.child("atlas-etc1");

		String outputAtlasFilename = "atlas_test.atlas";
		String pngInputDir = spritesFolder.path();
		String regularAtlasOutputDir = regularAtlasFolder.path();
		String etc1AtlasOutputDir = etc1AtlasFolder.path();

		boolean PACK = !true;

		if (PACK) {
			prepareTestAtlas(pngInputDir, regularAtlasOutputDir, outputAtlasFilename);
			prepareTestAtlas(pngInputDir, etc1AtlasOutputDir, outputAtlasFilename);
		}

		String regularAtlasFilePathString = regularAtlasFolder.child(outputAtlasFilename).path();
		String etc1AtlasFilePathString = etc1AtlasFolder.child(outputAtlasFilename).path();

		boolean COMPRESS = !true;

		if (COMPRESS) {
			ETC1AtlasCompressorSettings settings = ETC1AtlasCompressor.newCompressionSettings();
			settings.setAtlasFilePathString(etc1AtlasFilePathString);
			log();
			ETC1AtlasCompressionResult compressionResult = ETC1AtlasCompressor.compress(settings);
			log();
			compressionResult.print();
		}

		log("Showing compressed sprites");
		new LwjglApplication(new ETC1AtlasCompressorTest(regularAtlasFilePathString, etc1AtlasFilePathString), "", 1024, 768);

	}

	private static void prepareTestAtlas (String pngInputDir, String atlasOutputDir, String outputAtlasFilename) {
		log("pngInputDir", pngInputDir);
		log("atlasOutputDir", atlasOutputDir);
		log("outputAtlasFilename", outputAtlasFilename);

		TexturePacker.Settings atlasSettings = new TexturePacker.Settings();
		atlasSettings.debug = true;
		atlasSettings.maxWidth = 1024;
		atlasSettings.maxHeight = 1024;
		atlasSettings.format = Format.RGBA8888;

		TexturePacker.process(atlasSettings, pngInputDir, atlasOutputDir, outputAtlasFilename);
	}

	private static void log (String tag, Object message) {
		ETC1AtlasCompressor.log(tag, message);
	}

	private static void log (Object message) {
		ETC1AtlasCompressor.log(message);
	}

	private static void log () {
		ETC1AtlasCompressor.log();
	}

	/// -------------------------------------------------------------------------------------------------

	SpriteBatch batch;

	private TextureAtlas regularAtlas;
	private Array<Sprite> regularSprites;

	private TextureAtlas etc1Atlas;
	private Array<Sprite> etc1Sprites;

	public void create () {
		batch = new SpriteBatch();
		long start = System.currentTimeMillis();
		regularAtlas = new TextureAtlas(this.regularAtlasPathFile);
		regularSprites = regularAtlas.createSprites();
		long end = System.currentTimeMillis() - start;

		log("regular atlas", end);
		start = System.currentTimeMillis();
		etc1Atlas = new TextureAtlas(this.etc1AtlasPathFile);
		etc1Sprites = etc1Atlas.createSprites();
		end = System.currentTimeMillis() - start;
		log("etc1 atlas", end);

		float x = 0;
		float y = 0;
		for (int i = 0; i < regularSprites.size; i++) {
			Sprite sprite = regularSprites.get(i);
			sprite.setX(x);
			x = x + sprite.getWidth() * 0.9f;
			y = Math.max(y, sprite.getHeight());
		}
		x = 0;
		for (int i = 0; i < etc1Sprites.size; i++) {
			Sprite sprite = etc1Sprites.get(i);
			sprite.setX(x);
			sprite.setY(y * 1.1f);
			x = x + sprite.getWidth() * 0.9f;
		}

	}

	boolean renderRegular = true;
	boolean renderETC1 = !true;

	FPSLogger fps = new FPSLogger();

	public void render () {

		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		if (renderRegular) {
			batch.begin();
			renderTest(this.regularSprites);
			batch.end();
		}
		if (renderETC1) {
			batch.begin();
			renderTest(this.regularSprites);
			batch.end();
		}
		fps.log();
	}

	int WEIGHT = 300;

	private void renderTest (final Array<Sprite> sprites) {
		for (int K = 0; K < WEIGHT; K++) {
			for (final Sprite sprite : sprites) {
				sprite.draw(batch);
			}
		}
	}

	public void resize (int width, int height) {
		float m = 0.6f;
		batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth() * m, Gdx.graphics.getHeight() * m));
	}

	@Override
	public void pause () {
	}

	@Override
	public void resume () {
	}

	@Override
	public void dispose () {
	}

}
