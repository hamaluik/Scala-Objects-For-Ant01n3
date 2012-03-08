package org.sofa.opengl

import java.io.{File, InputStream, FileInputStream}
import scala.collection.mutable._
import org.sofa.math._
import org.sofa.nio._

/** Shader companion object. */
object Shader {
    val includeMatcher = "#include\\s+<([^>]+)>\\s*".r

    val includePath = scala.collection.mutable.ArrayBuffer[String]()
    
    /** Transform a text file into an array of strings. */
    def fileToArrayOfStrings(file:String):Array[String] = {
        streamToArrayOfStrings(locateFileName(file))
    }
    /** Transform a text file from a stream into an array of strings. */
    def streamToArrayOfStrings(in:InputStream):Array[String] = {
        val buf = new scala.collection.mutable.ArrayBuffer[String]
        val src = new scala.io.BufferedSource(in)
        src.getLines.foreach { line =>
        	if(line.startsWith("#include")) {
        	    val fileName = line match {
        	        case includeMatcher(file) => file 
        	        case _                    => throw new RuntimeException("invalid include statement '%s'".format(line))
        	    }
        	    buf ++= streamToArrayOfStrings(locateFileName(fileName))
        	} else {
        		buf += "%s%n".format(line)
        	}
        }

        buf.toArray
    }
    
    /** Try to open the given filename, and if this is not possible, try to
      * open it from a repository of shaders in each of the paths listed
      * in the `includePath` variable. Throw an exception if the file cannot
      * be open, else returns an input stream on it. */
    def locateFileName(fileName:String):InputStream = {
        var file = new File(fileName)
        if(! file.exists) {
            includePath.foreach { path =>
                val f = new File("%s/%s".format(path, fileName))
                if(f.exists) file = f
            }
        }
        
        if(! file.exists) throw new RuntimeException("cannot locate include file %s".format(fileName))
        
        new FileInputStream(file)
    }
}

/** Represents a shader, either vertex, fragment or geometry.
 *  
 * @param gl The SGL instance.
 * @param source An array of lines of code. 
 */
abstract class Shader(gl:SGL, val source:Array[String]) extends OpenGLObject(gl) {
    import gl._

    /** Try to open the given `sourceFile` on the file system and compile it. */
    def this(gl:SGL, sourceFile:String) = this(gl, Shader.fileToArrayOfStrings(sourceFile))

    /** Try to read a shader source from the given input `stream` and compile it. */
    def this(gl:SGL, stream:java.io.InputStream) = this(gl, Shader.streamToArrayOfStrings(stream))
    
    /** Kind of shader, vertex, fragment or geometry ? */
    protected val shaderType:Int
    
    /** Upload the source, compile it and check errors. */
    protected def init() {
        super.init(createShader(shaderType))
        checkErrors
        shaderSource(oid, source)
        compileShader(oid)
        checkErrors
        
        if(!getShaderCompileStatus(oid)) {
            val log = getShaderInfoLog(oid)
        	Console.err.println(log)
        	throw new RuntimeException("Cannot compile shader:%n%s".format(log))
        }
    }
    
    /** Release the shader. */
    override def dispose() {
        checkId
        deleteShader(oid)
        super.dispose
    }
}

/** A vertex shader/ */
class VertexShader(gl:SGL, source:Array[String]) extends Shader(gl, source) {
    protected val shaderType = gl.VERTEX_SHADER
    
    init
    
    /** Try to open the given `sourceFile` on the file system and compile it. */
    def this(gl:SGL, fileSource:String) = this(gl, Shader.fileToArrayOfStrings(fileSource))
    
    /** Try to read a shader source from the given input `stream` and compile it. */
    def this(gl:SGL, stream:java.io.InputStream) = this(gl, Shader.streamToArrayOfStrings(stream))
}

/** A fragment shader. */
class FragmentShader(gl:SGL, source:Array[String]) extends Shader(gl, source) {
    protected val shaderType = gl.FRAGMENT_SHADER
    
    init
    
    /** Try to open the given `sourceFile` on the file system and compile it. */
    def this(gl:SGL, fileSource:String) = this(gl, Shader.fileToArrayOfStrings(fileSource))

    /** Try to read a shader source from the given input `stream` and compile it. */
    def this(gl:SGL, stream:java.io.InputStream) = this(gl, Shader.streamToArrayOfStrings(stream))
}

/** Composition of several shaders into a program. */
class ShaderProgram(gl:SGL, shdrs:Shader*) extends OpenGLObject(gl) {
    import gl._
    
    /** Set of shaders. */
    protected val shaders = shdrs.toArray
    
    /** Locations of each uniform variable in the shader. */
    protected val uniformLocations = new HashMap[String, Int]
    
    /** Location of each vertex attribute variable in the shader. */
    protected val attributeLocations = new HashMap[String, Int]
    
    init
    
    protected def init() {
        super.init(createProgram)
        shaders.foreach { shader => attachShader(oid, shader.id) }
        linkProgram(oid)
        
        if(! getProgramLinkStatus(oid)) {
            val log = "error"
            //val log = getProgramInfoLog(oid)
            //Console.err.println(log)
            throw new RuntimeException("Cannot link shaders program:%n%s".format(log))
        }
        
        checkErrors
    }
    
    def use() {
        checkId
        useProgram(oid)
    }
    
    override def dispose() {
        checkId
        useProgram(0)
        shaders.foreach { shader =>
            detachShader(oid, shader.id)
            shader.dispose
        }
        deleteProgram(oid)
        super.dispose
    }
    
    def uniform(variable:String, value:Int) {
        checkId
        val loc = getUniformLocation(variable)
        gl.uniform(loc, value)
        checkErrors
    }
    
    def uniform(variable:String, value:Float) {
        checkId
        val loc = getUniformLocation(variable)
        gl.uniform(loc, value)
        checkErrors
    }
    
    def uniform(variable:String, value1:Int, value2:Int, value3:Int) {
        checkId
        val loc = getUniformLocation(variable)
        gl.uniform(loc, value1, value2, value3)
        checkErrors
    }
    
    def uniform(variable:String, value1:Float, value2:Float, value3:Float) {
        checkId
        val loc = getUniformLocation(variable)
        gl.uniform(loc, value1, value2, value3)
        checkErrors
    }
    
    def uniform(variable:String, value1:Float, value2:Float, value3:Float, value4:Float) {
        checkId
        val loc = getUniformLocation(variable)
        gl.uniform(loc, value1, value2, value3, value4)
        checkErrors
    }
    
    def uniform(variable:String, color:Rgba) {
        checkId
        val loc = getUniformLocation(variable)
        gl.uniform(loc, color)
        checkErrors
    }
    
    def uniform(variable:String, v:Array[Float]) {
        checkId
        gl.uniform(getUniformLocation(variable), v)
        checkErrors
    }
    
    def uniform(variable:String, v:Array[Double]) {
        checkId
        gl.uniform(getUniformLocation(variable), v)
        checkErrors
    }

    def uniform(variable:String, v:FloatBuffer) {
        checkId
        gl.uniform(getUniformLocation(variable), v)
        checkErrors
    }
    
    def uniform(variable:String, v:DoubleBuffer) {
        checkId
        gl.uniform(getUniformLocation(variable), v)
        checkErrors
    }
    
    def uniform(variable:String, v:NumberSeq) {
        checkId
        gl.uniform(getUniformLocation(variable), v.toFloatArray)	// Cannot pass Nio double or float arrays yet.
        checkErrors
    }

    def uniformMatrix(variable:String, matrix:Matrix4#ReturnType) {
        uniformMatrix(variable, matrix.toFloatBuffer)	// Matrices in shaders are made of floats
        checkErrors										// No way to use a double !!
    }

    def uniformMatrix(variable:String, matrix:Matrix4) {
        uniformMatrix(variable, matrix.toFloatBuffer)	// Matrices in shaders are made of floats
        checkErrors										// No way to use a double !!
    }

    def uniformMatrix(variable:String, matrix:Matrix3) {
        uniformMatrix(variable, matrix.toFloatBuffer)
        checkErrors
    }

    def uniformMatrix(variable:String, matrix:FloatBuffer) {
        checkId
        val loc = getUniformLocation(variable)
        if(matrix.size == 9)
            uniformMatrix3(loc, 1, false, matrix)
        else if(matrix.size == 16)
            uniformMatrix4(loc, 1, false, matrix)
        else throw new RuntimeException("matrix must be 9 (3x3) or 16 (4x4) floats");
    }
    
    def getAttribLocation(variable:String):Int ={
        checkId
        useProgram(oid)
        var loc = attributeLocations.get(variable).getOrElse {
            val l = gl.getAttribLocation(oid, variable) 
        	attributeLocations.put(variable, l)
            l
        }
        checkErrors
        loc
    }
    
    def getUniformLocation(variable:String):Int = {
        checkId
        useProgram(oid)
        val loc = uniformLocations.get(variable).getOrElse {
            var l = gl.getUniformLocation(oid, variable)
            uniformLocations.put(variable, l)
            l
        }
        checkErrors
        //uniformLocations.put(variable, loc)
        loc
    }
}