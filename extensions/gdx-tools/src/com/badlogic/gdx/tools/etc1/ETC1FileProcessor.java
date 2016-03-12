
package com.badlogic.gdx.tools.etc1;

import java.util.ArrayList;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.glutils.ETC1;
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
		System.out.println("Processing " + entry.inputFile);
		Pixmap pixmap = new Pixmap(new FileHandle(entry.inputFile));
		if (pixmap.getFormat() != Format.RGB888 && pixmap.getFormat() != Format.RGB565) {
			System.out.println("Converting from " + pixmap.getFormat() + " to RGB888!");
			Pixmap tmp = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), Format.RGB888);
			tmp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight());
			pixmap.dispose();
			pixmap = tmp;
		}
		ETC1.encodeImagePKM(pixmap).write(new FileHandle(entry.outputFile));
		pixmap.dispose();
	}

	@Override
	protected void processDir (Entry entryDir, ArrayList<Entry> value) throws Exception {
		if (!entryDir.outputDir.exists()) {
			if (!entryDir.outputDir.mkdirs()) throw new Exception("Couldn't create output directory '" + entryDir.outputDir + "'");
		}
	}
}
