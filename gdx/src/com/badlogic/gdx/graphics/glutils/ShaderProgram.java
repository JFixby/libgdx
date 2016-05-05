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

package com.badlogic.gdx.graphics.glutils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;

/** <p>
 * A shader program encapsulates a vertex and fragment shader pair linked to form a shader program useable with OpenGL ES 2.0.
 * </p>
 *
 * <p>
 * After construction a ShaderProgram can be used to draw {@link Mesh}. To make the GPU use a specific ShaderProgram the programs
 * {@link ShaderProgram#begin()} method must be used which effectively binds the program.
 * </p>
 *
 * <p>
 * When a ShaderProgram is bound one can set uniforms, vertex attributes and attributes as needed via the respective methods.
 * </p>
 *
 * <p>
 * A ShaderProgram can be unbound with a call to {@link ShaderProgram#end()}
 * </p>
 *
 * <p>
 * A ShaderProgram must be disposed via a call to {@link ShaderProgram#dispose()} when it is no longer needed
 * </p>
 *
 * <p>
 * ShaderPrograms are managed. In case the OpenGL context is lost all shaders get invalidated and have to be reloaded. This
 * happens on Android when a user switches to another application or receives an incoming call. Managed ShaderPrograms are
 * automatically reloaded when the OpenGL context is recreated so you don't have to do this manually.
 * </p>
 *
 * @author mzechner */
public class ShaderProgram implements Disposable {
	/** default name for position attributes **/
	public static final String POSITION_ATTRIBUTE = "a_position";
	/** default name for normal attributes **/
	public static final String NORMAL_ATTRIBUTE = "a_normal";
	/** default name for color attributes **/
	public static final String COLOR_ATTRIBUTE = "a_color";
	/** default name for texcoords attributes, append texture unit number **/
	public static final String TEXCOORD_ATTRIBUTE = "a_texCoord";
	/** default name for tangent attribute **/
	public static final String TANGENT_ATTRIBUTE = "a_tangent";
	/** default name for binormal attribute **/
	public static final String BINORMAL_ATTRIBUTE = "a_binormal";

	/** flag indicating whether attributes & uniforms must be present at all times **/
	public static boolean pedantic = true;

	/** code that is always added to the vertex shader code, typically used to inject a #version line. Note that this is added
	 * as-is, you should include a newline (`\n`) if needed. */
	public static String prependVertexCode = "";

	/** code that is always added to every fragment shader code, typically used to inject a #version line. Note that this is added
	 * as-is, you should include a newline (`\n`) if needed. */
	public static String prependFragmentCode = "";

	/** the list of currently available shaders **/
	private final static ObjectMap<Application, Array<ShaderProgram>> shaders = new ObjectMap<Application, Array<ShaderProgram>>();

	/** the log **/
	private String log = "";

	/** whether this program compiled successfully **/
	private boolean isCompiled;

	/** uniform lookup **/
	private final ObjectIntMap<String> uniforms = new ObjectIntMap<String>();

	/** uniform types **/
	private final ObjectIntMap<String> uniformTypes = new ObjectIntMap<String>();

	/** uniform sizes **/
	private final ObjectIntMap<String> uniformSizes = new ObjectIntMap<String>();

	/** uniform names **/
	private String[] uniformNames;

	/** attribute lookup **/
	private final ObjectIntMap<String> attributes = new ObjectIntMap<String>();

	/** attribute types **/
	private final ObjectIntMap<String> attributeTypes = new ObjectIntMap<String>();

	/** attribute sizes **/
	private final ObjectIntMap<String> attributeSizes = new ObjectIntMap<String>();

	/** attribute names **/
	private String[] attributeNames;

	/** program handle **/
	private int program;

	/** vertex shader handle **/
	private int vertexShaderHandle;

	/** fragment shader handle **/
	private int fragmentShaderHandle;

	/** matrix float buffer **/
	private final FloatBuffer matrix;

	/** vertex shader source **/
	private final String vertexShaderSource;

	/** fragment shader source **/
	private final String fragmentShaderSource;

	/** whether this shader was invalidated **/
	private boolean invalidated;

	/** reference count **/
	private final int refCount = 0;

	/** Constructs a new ShaderProgram and immediately compiles it.
	 *
	 * @param vertexShader the vertex shader
	 * @param fragmentShader the fragment shader */

	public ShaderProgram (String vertexShader, String fragmentShader) {
		if (vertexShader == null) {
			throw new IllegalArgumentException("vertex shader must not be null");
		}
		if (fragmentShader == null) {
			throw new IllegalArgumentException("fragment shader must not be null");
		}

		if (prependVertexCode != null && prependVertexCode.length() > 0) {
			vertexShader = prependVertexCode + vertexShader;
		}
		if (prependFragmentCode != null && prependFragmentCode.length() > 0) {
			fragmentShader = prependFragmentCode + fragmentShader;
		}

		this.vertexShaderSource = vertexShader;
		this.fragmentShaderSource = fragmentShader;
		this.matrix = BufferUtils.newFloatBuffer(16);

		this.compileShaders(vertexShader, fragmentShader);
		if (this.isCompiled()) {
			this.fetchAttributes();
			this.fetchUniforms();
			this.addManagedShader(Gdx.app, this);
		}
	}

	public ShaderProgram (final FileHandle vertexShader, final FileHandle fragmentShader) {
		this(vertexShader.readString(), fragmentShader.readString());
	}

	/** Loads and compiles the shaders, creates a new program and links the shaders.
	 *
	 * @param vertexShader
	 * @param fragmentShader */
	private void compileShaders (final String vertexShader, final String fragmentShader) {
		this.vertexShaderHandle = this.loadShader(GL20.GL_VERTEX_SHADER, vertexShader);
		this.fragmentShaderHandle = this.loadShader(GL20.GL_FRAGMENT_SHADER, fragmentShader);

		if (this.vertexShaderHandle == -1 || this.fragmentShaderHandle == -1) {
			this.isCompiled = false;
			return;
		}

		this.program = this.linkProgram(this.createProgram());
		if (this.program == -1) {
			this.isCompiled = false;
			return;
		}

		this.isCompiled = true;
	}

	private int loadShader (final int type, final String source) {
		final GL20 gl = Gdx.gl20;
		final IntBuffer intbuf = BufferUtils.newIntBuffer(1);

		final int shader = gl.glCreateShader(type);
		if (shader == 0) {
			return -1;
		}

		gl.glShaderSource(shader, source);
		gl.glCompileShader(shader);
		gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, intbuf);

		final int compiled = intbuf.get(0);
		if (compiled == 0) {
// gl.glGetShaderiv(shader, GL20.GL_INFO_LOG_LENGTH, intbuf);
// int infoLogLength = intbuf.get(0);
// if (infoLogLength > 1) {
			final String infoLog = gl.glGetShaderInfoLog(shader);
			this.log += infoLog;
// }
			return -1;
		}

		return shader;
	}

	protected int createProgram () {
		final GL20 gl = Gdx.gl20;
		final int program = gl.glCreateProgram();
		return program != 0 ? program : -1;
	}

	private int linkProgram (final int program) {
		final GL20 gl = Gdx.gl20;
		if (program == -1) {
			return -1;
		}

		gl.glAttachShader(program, this.vertexShaderHandle);
		gl.glAttachShader(program, this.fragmentShaderHandle);
		gl.glLinkProgram(program);

		final ByteBuffer tmp = ByteBuffer.allocateDirect(4);
		tmp.order(ByteOrder.nativeOrder());
		final IntBuffer intbuf = tmp.asIntBuffer();

		gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, intbuf);
		final int linked = intbuf.get(0);
		if (linked == 0) {
// Gdx.gl20.glGetProgramiv(program, GL20.GL_INFO_LOG_LENGTH, intbuf);
// int infoLogLength = intbuf.get(0);
// if (infoLogLength > 1) {
			this.log = Gdx.gl20.glGetProgramInfoLog(program);
// }
			return -1;
		}

		return program;
	}

	final static IntBuffer intbuf = BufferUtils.newIntBuffer(1);

	/** @return the log info for the shader compilation and program linking stage. The shader needs to be bound for this method to
	 *         have an effect. */
	public String getLog () {
		if (this.isCompiled) {
// Gdx.gl20.glGetProgramiv(program, GL20.GL_INFO_LOG_LENGTH, intbuf);
// int infoLogLength = intbuf.get(0);
// if (infoLogLength > 1) {
			this.log = Gdx.gl20.glGetProgramInfoLog(this.program);
// }
			return this.log;
		} else {
			return this.log;
		}
	}

	/** @return whether this ShaderProgram compiled successfully. */
	public boolean isCompiled () {
		return this.isCompiled;
	}

	private int fetchAttributeLocation (final String name) {
		final GL20 gl = Gdx.gl20;
		// -2 == not yet cached
		// -1 == cached but not found
		int location;
		if ((location = this.attributes.get(name, -2)) == -2) {
			location = gl.glGetAttribLocation(this.program, name);
			this.attributes.put(name, location);
		}
		return location;
	}

	private int fetchUniformLocation (final String name) {
		return this.fetchUniformLocation(name, pedantic);
	}

	public int fetchUniformLocation (final String name, final boolean pedantic) {
		final GL20 gl = Gdx.gl20;
		// -2 == not yet cached
		// -1 == cached but not found
		int location;
		if ((location = this.uniforms.get(name, -2)) == -2) {
			location = gl.glGetUniformLocation(this.program, name);
			if (location == -1 && pedantic) {
				throw new IllegalArgumentException("no uniform with name '" + name + "' in shader");
			}
			this.uniforms.put(name, location);
		}
		return location;
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value the value */
	public void setUniformi (final String name, final int value) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform1i(location, value);
	}

	public void setUniformi (final int location, final int value) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform1i(location, value);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value1 the first value
	 * @param value2 the second value */
	public void setUniformi (final String name, final int value1, final int value2) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform2i(location, value1, value2);
	}

	public void setUniformi (final int location, final int value1, final int value2) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform2i(location, value1, value2);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value1 the first value
	 * @param value2 the second value
	 * @param value3 the third value */
	public void setUniformi (final String name, final int value1, final int value2, final int value3) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform3i(location, value1, value2, value3);
	}

	public void setUniformi (final int location, final int value1, final int value2, final int value3) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform3i(location, value1, value2, value3);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value1 the first value
	 * @param value2 the second value
	 * @param value3 the third value
	 * @param value4 the fourth value */
	public void setUniformi (final String name, final int value1, final int value2, final int value3, final int value4) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform4i(location, value1, value2, value3, value4);
	}

	public void setUniformi (final int location, final int value1, final int value2, final int value3, final int value4) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform4i(location, value1, value2, value3, value4);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value the value */
	public void setUniformf (final String name, final float value) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform1f(location, value);
	}

	public void setUniformf (final int location, final float value) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform1f(location, value);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value1 the first value
	 * @param value2 the second value */
	public void setUniformf (final String name, final float value1, final float value2) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform2f(location, value1, value2);
	}

	public void setUniformf (final int location, final float value1, final float value2) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform2f(location, value1, value2);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value1 the first value
	 * @param value2 the second value
	 * @param value3 the third value */
	public void setUniformf (final String name, final float value1, final float value2, final float value3) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform3f(location, value1, value2, value3);
	}

	public void setUniformf (final int location, final float value1, final float value2, final float value3) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform3f(location, value1, value2, value3);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param value1 the first value
	 * @param value2 the second value
	 * @param value3 the third value
	 * @param value4 the fourth value */
	public void setUniformf (final String name, final float value1, final float value2, final float value3, final float value4) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform4f(location, value1, value2, value3, value4);
	}

	public void setUniformf (final int location, final float value1, final float value2, final float value3, final float value4) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform4f(location, value1, value2, value3, value4);
	}

	public void setUniform1fv (final String name, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform1fv(location, length, values, offset);
	}

	public void setUniform1fv (final int location, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform1fv(location, length, values, offset);
	}

	public void setUniform2fv (final String name, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform2fv(location, length / 2, values, offset);
	}

	public void setUniform2fv (final int location, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform2fv(location, length / 2, values, offset);
	}

	public void setUniform3fv (final String name, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform3fv(location, length / 3, values, offset);
	}

	public void setUniform3fv (final int location, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform3fv(location, length / 3, values, offset);
	}

	public void setUniform4fv (final String name, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchUniformLocation(name);
		gl.glUniform4fv(location, length / 4, values, offset);
	}

	public void setUniform4fv (final int location, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniform4fv(location, length / 4, values, offset);
	}

	/** Sets the uniform matrix with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param matrix the matrix */
	public void setUniformMatrix (final String name, final Matrix4 matrix) {
		this.setUniformMatrix(name, matrix, false);
	}

	/** Sets the uniform matrix with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param matrix the matrix
	 * @param transpose whether the matrix should be transposed */
	public void setUniformMatrix (final String name, final Matrix4 matrix, final boolean transpose) {
		this.setUniformMatrix(this.fetchUniformLocation(name), matrix, transpose);
	}

	public void setUniformMatrix (final int location, final Matrix4 matrix) {
		this.setUniformMatrix(location, matrix, false);
	}

	public void setUniformMatrix (final int location, final Matrix4 matrix, final boolean transpose) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniformMatrix4fv(location, 1, transpose, matrix.val, 0);
	}

	/** Sets the uniform matrix with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param matrix the matrix */
	public void setUniformMatrix (final String name, final Matrix3 matrix) {
		this.setUniformMatrix(name, matrix, false);
	}

	/** Sets the uniform matrix with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param matrix the matrix
	 * @param transpose whether the uniform matrix should be transposed */
	public void setUniformMatrix (final String name, final Matrix3 matrix, final boolean transpose) {
		this.setUniformMatrix(this.fetchUniformLocation(name), matrix, transpose);
	}

	public void setUniformMatrix (final int location, final Matrix3 matrix) {
		this.setUniformMatrix(location, matrix, false);
	}

	public void setUniformMatrix (final int location, final Matrix3 matrix, final boolean transpose) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniformMatrix3fv(location, 1, transpose, matrix.val, 0);
	}

	/** Sets an array of uniform matrices with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param buffer buffer containing the matrix data
	 * @param transpose whether the uniform matrix should be transposed */
	public void setUniformMatrix3fv (final String name, final FloatBuffer buffer, final int count, final boolean transpose) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		buffer.position(0);
		final int location = this.fetchUniformLocation(name);
		gl.glUniformMatrix3fv(location, count, transpose, buffer);
	}

	/** Sets an array of uniform matrices with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param buffer buffer containing the matrix data
	 * @param transpose whether the uniform matrix should be transposed */
	public void setUniformMatrix4fv (final String name, final FloatBuffer buffer, final int count, final boolean transpose) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		buffer.position(0);
		final int location = this.fetchUniformLocation(name);
		gl.glUniformMatrix4fv(location, count, transpose, buffer);
	}

	public void setUniformMatrix4fv (final int location, final float[] values, final int offset, final int length) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUniformMatrix4fv(location, length / 16, false, values, offset);
	}

	public void setUniformMatrix4fv (final String name, final float[] values, final int offset, final int length) {
		this.setUniformMatrix4fv(this.fetchUniformLocation(name), values, offset, length);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param values x and y as the first and second values respectively */
	public void setUniformf (final String name, final Vector2 values) {
		this.setUniformf(name, values.x, values.y);
	}

	public void setUniformf (final int location, final Vector2 values) {
		this.setUniformf(location, values.x, values.y);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param values x, y and z as the first, second and third values respectively */
	public void setUniformf (final String name, final Vector3 values) {
		this.setUniformf(name, values.x, values.y, values.z);
	}

	public void setUniformf (final int location, final Vector3 values) {
		this.setUniformf(location, values.x, values.y, values.z);
	}

	/** Sets the uniform with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the name of the uniform
	 * @param values r, g, b and a as the first through fourth values respectively */
	public void setUniformf (final String name, final Color values) {
		this.setUniformf(name, values.r, values.g, values.b, values.a);
	}

	public void setUniformf (final int location, final Color values) {
		this.setUniformf(location, values.r, values.g, values.b, values.a);
	}

	/** Sets the vertex attribute with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the attribute name
	 * @param size the number of components, must be >= 1 and <= 4
	 * @param type the type, must be one of GL20.GL_BYTE, GL20.GL_UNSIGNED_BYTE, GL20.GL_SHORT,
	 *           GL20.GL_UNSIGNED_SHORT,GL20.GL_FIXED, or GL20.GL_FLOAT. GL_FIXED will not work on the desktop
	 * @param normalize whether fixed point data should be normalized. Will not work on the desktop
	 * @param stride the stride in bytes between successive attributes
	 * @param buffer the buffer containing the vertex attributes. */
	public void setVertexAttribute (final String name, final int size, final int type, final boolean normalize, final int stride,
		final Buffer buffer) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchAttributeLocation(name);
		if (location == -1) {
			return;
		}
		gl.glVertexAttribPointer(location, size, type, normalize, stride, buffer);
	}

	public void setVertexAttribute (final int location, final int size, final int type, final boolean normalize, final int stride,
		final Buffer buffer) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glVertexAttribPointer(location, size, type, normalize, stride, buffer);
	}

	/** Sets the vertex attribute with the given name. The {@link ShaderProgram} must be bound for this to work.
	 *
	 * @param name the attribute name
	 * @param size the number of components, must be >= 1 and <= 4
	 * @param type the type, must be one of GL20.GL_BYTE, GL20.GL_UNSIGNED_BYTE, GL20.GL_SHORT,
	 *           GL20.GL_UNSIGNED_SHORT,GL20.GL_FIXED, or GL20.GL_FLOAT. GL_FIXED will not work on the desktop
	 * @param normalize whether fixed point data should be normalized. Will not work on the desktop
	 * @param stride the stride in bytes between successive attributes
	 * @param offset byte offset into the vertex buffer object bound to GL20.GL_ARRAY_BUFFER. */
	public void setVertexAttribute (final String name, final int size, final int type, final boolean normalize, final int stride,
		final int offset) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchAttributeLocation(name);
		if (location == -1) {
			return;
		}
		gl.glVertexAttribPointer(location, size, type, normalize, stride, offset);
	}

	public void setVertexAttribute (final int location, final int size, final int type, final boolean normalize, final int stride,
		final int offset) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glVertexAttribPointer(location, size, type, normalize, stride, offset);
	}

	/** Makes OpenGL ES 2.0 use this vertex and fragment shader pair. When you are done with this shader you have to call
	 * {@link ShaderProgram#end()}. */
	public void begin () {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glUseProgram(this.program);
	}

	/** Disables this shader. Must be called when one is done with the shader. Don't mix it with dispose, that will release the
	 * shader resources. */
	public void end () {
		final GL20 gl = Gdx.gl20;
		gl.glUseProgram(0);
	}

	/** Disposes all resources associated with this shader. Must be called when the shader is no longer used. */
	@Override
	public void dispose () {
		final GL20 gl = Gdx.gl20;
		gl.glUseProgram(0);
		gl.glDeleteShader(this.vertexShaderHandle);
		gl.glDeleteShader(this.fragmentShaderHandle);
		gl.glDeleteProgram(this.program);
		if (shaders.get(Gdx.app) != null) {
			shaders.get(Gdx.app).removeValue(this, true);
		}
	}

	/** Disables the vertex attribute with the given name
	 *
	 * @param name the vertex attribute name */
	public void disableVertexAttribute (final String name) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchAttributeLocation(name);
		if (location == -1) {
			return;
		}
		gl.glDisableVertexAttribArray(location);
	}

	public void disableVertexAttribute (final int location) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glDisableVertexAttribArray(location);
	}

	/** Enables the vertex attribute with the given name
	 *
	 * @param name the vertex attribute name */
	public void enableVertexAttribute (final String name) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		final int location = this.fetchAttributeLocation(name);
		if (location == -1) {
			return;
		}
		gl.glEnableVertexAttribArray(location);
	}

	public void enableVertexAttribute (final int location) {
		final GL20 gl = Gdx.gl20;
		this.checkManaged();
		gl.glEnableVertexAttribArray(location);
	}

	private void checkManaged () {
		if (this.invalidated) {
			this.compileShaders(this.vertexShaderSource, this.fragmentShaderSource);
			this.invalidated = false;
		}
	}

	private void addManagedShader (final Application app, final ShaderProgram shaderProgram) {
		Array<ShaderProgram> managedResources = shaders.get(app);
		if (managedResources == null) {
			managedResources = new Array<ShaderProgram>();
		}
		managedResources.add(shaderProgram);
		shaders.put(app, managedResources);
	}

	/** Invalidates all shaders so the next time they are used new handles are generated
	 * @param app */
	public static void invalidateAllShaderPrograms (final Application app) {
		if (Gdx.gl20 == null) {
			return;
		}

		final Array<ShaderProgram> shaderArray = shaders.get(app);
		if (shaderArray == null) {
			return;
		}

		for (int i = 0; i < shaderArray.size; i++) {
			shaderArray.get(i).invalidated = true;
			shaderArray.get(i).checkManaged();
		}
	}

	public static void clearAllShaderPrograms (final Application app) {
		shaders.remove(app);
	}

	public static String getManagedStatus () {
		final StringBuilder builder = new StringBuilder();
		final int i = 0;
		builder.append("Managed shaders/app: { ");
		for (final Application app : shaders.keys()) {
			builder.append(shaders.get(app).size);
			builder.append(" ");
		}
		builder.append("}");
		return builder.toString();
	}

	/** @return the number of managed shader programs currently loaded */
	public static int getNumManagedShaderPrograms () {
		return shaders.get(Gdx.app).size;
	}

	/** Sets the given attribute
	 *
	 * @param name the name of the attribute
	 * @param value1 the first value
	 * @param value2 the second value
	 * @param value3 the third value
	 * @param value4 the fourth value */
	public void setAttributef (final String name, final float value1, final float value2, final float value3, final float value4) {
		final GL20 gl = Gdx.gl20;
		final int location = this.fetchAttributeLocation(name);
		gl.glVertexAttrib4f(location, value1, value2, value3, value4);
	}

	IntBuffer params = BufferUtils.newIntBuffer(1);
	IntBuffer type = BufferUtils.newIntBuffer(1);

	private void fetchUniforms () {
		this.params.clear();
		Gdx.gl20.glGetProgramiv(this.program, GL20.GL_ACTIVE_UNIFORMS, this.params);
		final int numUniforms = this.params.get(0);

		this.uniformNames = new String[numUniforms];

		for (int i = 0; i < numUniforms; i++) {
			this.params.clear();
			this.params.put(0, 1);
			this.type.clear();
			final String name = Gdx.gl20.glGetActiveUniform(this.program, i, this.params, this.type);
			final int location = Gdx.gl20.glGetUniformLocation(this.program, name);
			this.uniforms.put(name, location);
			this.uniformTypes.put(name, this.type.get(0));
			this.uniformSizes.put(name, this.params.get(0));
			this.uniformNames[i] = name;
		}
	}

	private void fetchAttributes () {
		this.params.clear();
		Gdx.gl20.glGetProgramiv(this.program, GL20.GL_ACTIVE_ATTRIBUTES, this.params);
		final int numAttributes = this.params.get(0);

		this.attributeNames = new String[numAttributes];

		for (int i = 0; i < numAttributes; i++) {
			this.params.clear();
			this.params.put(0, 1);
			this.type.clear();
			final String name = Gdx.gl20.glGetActiveAttrib(this.program, i, this.params, this.type);
			final int location = Gdx.gl20.glGetAttribLocation(this.program, name);
			this.attributes.put(name, location);
			this.attributeTypes.put(name, this.type.get(0));
			this.attributeSizes.put(name, this.params.get(0));
			this.attributeNames[i] = name;
		}
	}

	/** @param name the name of the attribute
	 * @return whether the attribute is available in the shader */
	public boolean hasAttribute (final String name) {
		return this.attributes.containsKey(name);
	}

	/** @param name the name of the attribute
	 * @return the type of the attribute, one of {@link GL20#GL_FLOAT}, {@link GL20#GL_FLOAT_VEC2} etc. */
	public int getAttributeType (final String name) {
		return this.attributeTypes.get(name, 0);
	}

	/** @param name the name of the attribute
	 * @return the location of the attribute or -1. */
	public int getAttributeLocation (final String name) {
		return this.attributes.get(name, -1);
	}

	/** @param name the name of the attribute
	 * @return the size of the attribute or 0. */
	public int getAttributeSize (final String name) {
		return this.attributeSizes.get(name, 0);
	}

	/** @param name the name of the uniform
	 * @return whether the uniform is available in the shader */
	public boolean hasUniform (final String name) {
		return this.uniforms.containsKey(name);
	}

	/** @param name the name of the uniform
	 * @return the type of the uniform, one of {@link GL20#GL_FLOAT}, {@link GL20#GL_FLOAT_VEC2} etc. */
	public int getUniformType (final String name) {
		return this.uniformTypes.get(name, 0);
	}

	/** @param name the name of the uniform
	 * @return the location of the uniform or -1. */
	public int getUniformLocation (final String name) {
		return this.uniforms.get(name, -1);
	}

	/** @param name the name of the uniform
	 * @return the size of the uniform or 0. */
	public int getUniformSize (final String name) {
		return this.uniformSizes.get(name, 0);
	}

	/** @return the attributes */
	public String[] getAttributes () {
		return this.attributeNames;
	}

	/** @return the uniforms */
	public String[] getUniforms () {
		return this.uniformNames;
	}

	/** @return the source of the vertex shader */
	public String getVertexShaderSource () {
		return this.vertexShaderSource;
	}

	/** @return the source of the fragment shader */
	public String getFragmentShaderSource () {
		return this.fragmentShaderSource;
	}
}
