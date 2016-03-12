
package com.badlogic.gdx.tools.etc1;

import java.util.ArrayList;

import com.badlogic.gdx.tools.FileProcessor;

public class ETC1FileProcessor extends FileProcessor {
	ETC1FileProcessor () {
		addInputSuffix(".png");
		addInputSuffix(".jpg");
		addInputSuffix(".jpeg");
		addInputSuffix(".bmp");
		setOutputSuffix(".etc1");
	}

	@Override
	protected void processFile (Entry entry) throws Exception {
		String inputFilePath = entry.inputFile.getAbsolutePath();
		String outputFilePath = entry.outputFile.getAbsolutePath();
		ETC1Compressor.compress(inputFilePath, outputFilePath);
	}

	@Override
	protected void processDir (Entry entryDir, ArrayList<Entry> value) throws Exception {
		if (!entryDir.outputDir.exists()) {
			if (!entryDir.outputDir.mkdirs()) throw new Exception("Couldn't create output directory '" + entryDir.outputDir + "'");
		}
	}
}
