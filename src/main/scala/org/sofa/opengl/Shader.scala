package org.sofa.opengl

import java.io.{File, InputStream, FileInputStream, IOException}
import scala.collection.mutable._
import org.sofa.math._
import org.sofa.nio._

/** Shader companion object. */
object Shader {
    /** A regular expression that matches any line that contains an include. */
    val includeMatcher = "#include\\s+<([^>]+)>\\s*".r

    /** The set of paths to try to load a shader. */
    val includePath = scala.collection.mutable.ArrayBuffer[String]()

    /** A way to load a shader source. */
    var loader:ShaderLoader = new DefaultShaderLoader()
    
    /** Transform a text file into an array of strings. */
    def fileToArrayOfStrings(file:String):Array[String] = {
        streamToArrayOfStrings(loader.open(file))
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
        	    buf ++= streamToArrayOfStrings(loader.open(fileName))
        	} else {
        		buf += "%s%n".format(line)
        	}
        }

        buf.toArray
    }
}

/** Pluggable loader for shader sources. */
trait ShaderLoader {
    /** Try to open a resource, or throw an IOException if not available. */
    def open(resource:String):InputStream
}

/** Default loader for shaders, based on files and the include path.
  * This loader tries to open the given resource directly, then if not
  * found, tries to find it in each of the pathes provided by the include
  * path. If not found it throws an IOException. */
class DefaultShaderLoader extends ShaderLoader {
    def open(resource:String):InputStream = {
        var file = new File(resource)
        if(file.exists) {
            new FileInputStream(file)
        } else {
            val sep = sys.props.get("file.separator").get

            Shader.includePath.find(path => (new File("%s%s%s".format(path, sep, resource))).exists) match {
                case path:Some[String] => {new FileInputStream(new File("%s%s%s".format(path.get,sep,resource))) }
                case None => { throw new IOException("cannot locate shader %s".format(resource)) }
            }
        }
    }
}

object ShaderProgram {
    /** Create a new shader program from a vertex shader and a fragment shader. */
    def apply(gl:SGL, name:String, vertexShaderFileName:String, fragmentShaderFileName:String):ShaderProgram = {
    	new ShaderProgram(gl, name,
                new VertexShader(gl, name, vertexShaderFileName),
    			new FragmentShader(gl, name, fragmentShaderFileName))
    }
}

/** Represents a shader, either vertex, fragment or geometry.
 *  
 * @param gl The SGL instance.
 * @param source An array of lines of code. 
 */
abstract class Shader(gl:SGL, val name:String, val source:Array[String]) extends OpenGLObject(gl) {
    import gl._

    /** Try to open the given `sourceFile` on the file system and compile it. */
    def this(gl:SGL, name:String, sourceFile:String) = this(gl, name, Shader.fileToArrayOfStrings(sourceFile))

    /** Try to read a shader source from the given input `stream` and compile it. */
    def this(gl:SGL, name:String, stream:java.io.InputStream) = this(gl, name, Shader.streamToArrayOfStrings(stream))
    
    /** Kind of shader, vertex, fragment or geometry ? */
    protected val shaderType:Int
    
    /** Upload the source, compile it and check errors. */
    protected def init() {
        checkErrors
        super.init(createShader(shaderType))
        checkErrors
        shaderSource(oid, source)
        checkErrors
        compileShader(oid)
        checkErrors
        
        if(!getShaderCompileStatus(oid)) {
            val log = getShaderInfoLog(oid)
        	Console.err.println(log)
        	throw new RuntimeException("Cannot compile shader %s:%n%s".format(name, log))
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
class VertexShader(gl:SGL, name:String, source:Array[String]) extends Shader(gl, name, source) {
    protected val shaderType = gl.VERTEX_SHADER
    
    init
    
    /** Try to open the given `sourceFile` on the file system and compile it. */
    def this(gl:SGL, name:String, fileSource:String) = this(gl, name, Shader.fileToArrayOfStrings(fileSource))
    
    /** Try to read a shader source from the given input `stream` and compile it. */
    def this(gl:SGL, name:String, stream:java.io.InputStream) = this(gl, name, Shader.streamToArrayOfStrings(stream))
}

/** A fragment shader. */
class FragmentShader(gl:SGL, name:String, source:Array[String]) extends Shader(gl, name, source) {
    protected val shaderType = gl.FRAGMENT_SHADER
    
    init
    
    /** Try to open the given `sourceFile` on the file system and compile it. */
    def this(gl:SGL, name:String, fileSource:String) = this(gl, name, Shader.fileToArrayOfStrings(fileSource))

    /** Try to read a shader source from the given input `stream` and compile it. */
    def this(gl:SGL, name:String, stream:java.io.InputStream) = this(gl, name, Shader.streamToArrayOfStrings(stream))
}

/** Composition of several shaders into a program. */
class ShaderProgram(gl:SGL, val name:String, shdrs:Shader*) extends OpenGLObject(gl) {
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
            throw new RuntimeException("Cannot link shaders program %s:%n%s".format(name, log))
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
    
    def getAttribLocation(variable:String):Int = {
        var loc = attributeLocations.get(variable).getOrElse {
        	checkId
        	useProgram(oid)
            val l = gl.getAttribLocation(oid, variable) 
            checkErrors
            if(l >= 0) {
            	attributeLocations.put(variable, l)
            } else {
            	throw new RuntimeException("Cannot find attribute %s in program %s".format(variable, name))
            }
            l
        }
        loc
    }
    
    def getUniformLocation(variable:String):Int = {
        val loc = uniformLocations.get(variable).getOrElse {
        	checkId
        	useProgram(oid)
            var l = gl.getUniformLocation(oid, variable)
            checkErrors
            if(l >= 0) {
            	uniformLocations.put(variable, l)
            } else {
            	throw new RuntimeException("Cannot find uniform %s in program %s".format(variable, name))
            }
            l
        }
        //uniformLocations.put(variable, loc)
        loc
    }
}