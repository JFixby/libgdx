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

import com.badlogic.gdx.graphics.glutils.ETC1.ETC1Data;

public class ETC1CompressionResult {

	private ETC1Data pkm;

	public ETC1Data getETC1Data () {
		return pkm;
	}

	public void setETC1Data (ETC1Data pkm) {
		this.pkm = pkm;
	}

}
