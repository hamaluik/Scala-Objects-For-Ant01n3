package org.sofa.gfx.mesh

import scala.language.implicitConversions

import org.sofa.FileLoader
import org.sofa.nio._
import org.sofa.gfx._
import org.sofa.gfx.io.collada.ColladaFile

import scala.collection.mutable.HashMap



// TODO
// - Instanced mesh to use instanced rendering. 
// - allow a mesh to have several VAs, paving the way for multi mesh.
// - simplify the API.
// - Make MeshAttribute and MeshElement traits. Create base implementations.
// - Specify update() as an interface method for Mesh.
// - change EditableMesh to use these MeshAttribute (actually it duplicates them).
// - for dynamic meshes, use the VertexAttribute names or the name of the user attributes
//   to specify what to update (see TrianglesMesh as an example).



/** Pluggable loader for mesh sources. */
trait MeshLoader extends FileLoader {
    /** Try to open a resource, and inside this resource a given
      * `geometry` part, or throw an IOException if not available. */
    def open(resource:String, geometry:String):Mesh
}


/** Default loader for meshes, based on files and the include path, using
  * the Collada format, to read the geometry of the object.
  * This loader tries to open the given resource directly, then if not
  * found, tries to find it in each of the pathes provided by the include
  * path of [[org.sofa.gfx.io.collada.ColladaFile]]. If not found it throws an IOException. */
class ColladaMeshLoader extends MeshLoader {
    def open(resource:String, geometry:String):Mesh = {
    	val file = new ColladaFile(resource)

    	file.library.geometry(geometry).get.mesh.toMesh 
    }
}


/** Thrown when the mesh should have a vertex array but have not. */
class NoVertexArrayException(msg:String) extends Exception(msg)

/** Thrown when a vertex attribute is not declared in a mesh. */
class NoSuchVertexAttributeException(msg:String) extends Exception(msg)

/** Thrown when a vertex index is out of bounds in a vertex attribute. */
class InvalidVertexException(msg:String) extends Exception(msg)

/** Thrown when a vertex component index is out of bounds. */
class InvalidVertexComponentException(msg:String) extends Exception(msg)

/** Thrown when a primitive index is out of bounds. */
class InvalidPrimitiveException(msg:String) extends Exception(msg)

/** Thrown when a primitive vertex index is out of bounds. */
class InvalidPrimitiveVertexException(msg:String) extends Exception(msg)


object Mesh {
	var loader = new ColladaMeshLoader()
}


/** A mesh is a set of vertex data.
  * 
  * A mesh is a set of vertex attribute data (array buffers or vertex buffers in
  * the OpenGL terminology). They are roughly composed of one or more arrays of
  * floats associated with an optionnal set of indices in these attributes to
  * tell how to draw the data (an element buffer in OpenGL jargon).
  *
  * The mesh is not usable as is in an OpenGL program, you must transform it into a
  * [[org.sofa.gfx.VertexArray]]. A vertex array in the OpenGL langua is a set of
  * array buffers and an optional element buffer with all the settings needed to
  * render them as vertex data.
  *
  * The mesh acts as a factory to produce vertex arrays. You can create as many
  * vertex arrays as you need with one mesh (TODO: actually but this could change in the
  * future with the idea that a mesh is tied to its vertex array). However dynamic
  * meshes, that is meshes that are able to update their attribute data at any time,
  * always remember the last produced vertex array to allow to update this last one
  * only. It is better therefore to allocate only one vertex array with the mesh.
  *
  * Most of the time, a mesh can replace the vertex array to draw a model. In this
  * case use the mesh to create one vertex array only that will be stored in it.
  * This one will reused if the mesh changes.
  *
  * TODO: The arrays stored by the mesh actually are Nio buffers. This means that the mesh
  * owns its own memory to store vertex and element data, then needs to transfer it
  * to OpenGL when creating a vertex array or updating it. This is coherent with the
  * vision of mesh as a factory for vertex arrays. However if a mesh is tied to one 
  * vertex array only, the internal arrays of the mesh could be mapping of real OpenGL
  * buffers to avoid copy. This could easily be done by making MeshAttribute and MeshElement
  * traits and creating concrete classes that either map or store data.
  */
trait Mesh {
	import VertexAttribute._

	/** Last produced vertex array. */
	protected[this] var va:VertexArray = _

	/** Last used shader for the vertex array. */
	protected[this] var sh:ShaderProgram = _

	/** Set of user defined vertex attributes. Allocated on demand. */
	protected[this] var meshAttributes:HashMap[String, MeshAttribute] = null

	/** Release the resource of this mesh, the mesh is no more usable after this. */
	def dispose() { if(va ne null) va.dispose }

	/** Declare a vertex attribute `name` for the mesh.
	  *
	  * The attribute is made of the given number of `components` per vertex. For example,
	  * if this is a point in 3D there are 3 components. If this is a 2D texture UV
	  * coordinates, there are 2 components. */
	def addAttribute(name:VertexAttribute, components:Int) { addAttribute(name.toString, components) }

	/** Declare a vertex attribute `name` for the mesh.
	  *
	  * The attribute is made of the given number of `components` per vertex. For example,
	  * if this is a point in 3D there are 3 components. If this is a 2D texture UV
	  * coordinates, there are 2 components. */
	def addAttribute(name:String, components:Int) { addMeshAttribute(name, components) }

	/** Internal method used to declare a new attribute, its `name` and the
	  * number of `components`, that is the number of values per vertex.
	  * The returned [[MeshAttribute]] is stored. */
	protected def addMeshAttribute(name:String, components:Int):MeshAttribute = {
		// if(meshAttributes eq null)
		// 	meshAttributes = new HashMap[String, MeshAttribute]()

		// val meshAttr = new MeshAttribute(name, components, vertexCount)

		// meshAttributes += name -> meshAttr

		// meshAttr		
		addMeshAttribute(name, components, vertexCount)
	}

	protected def addMeshAttribute(name:String, components:Int, nVertices:Int):MeshAttribute = {
		if(meshAttributes eq null)
			meshAttributes = new HashMap[String, MeshAttribute]()

		val meshAttr = new MeshAttribute(name, components, nVertices)

		meshAttributes += name -> meshAttr

		meshAttr
	}

	/** Change the value of an attribute for the given `vertex`. The `values` must
	  * have as many elements as the attribute has components.
	  * @param name The attribute name.
	  * @param vertex The vertex tied to this attribute values.
	  * @param values a set of floats one for each component. */
	def setAttribute(name:String, vertex:Int, values:Float*) {
		meshAttribute(name).set(vertex, values:_*)
	}

	/** Number of vertices in the mesh. */
	def vertexCount:Int

	/** Number of elements (vertices) for one primitive. For
	  * example lines uses 2 elements, triangles 3, etc. */
	def elementsPerPrimitive:Int

	/** Internal method used to access a vertex attribute by its name 
	  * under the form of the [[MeshAttribute]] handling it. */
	protected def meshAttribute(name:String):MeshAttribute = {
		if(meshAttributes ne null) meshAttributes.get(name).getOrElse { 
			throw new NoSuchVertexAttributeException(s"mesh has no attribute named ${name}")
		} else {
			null
		}
	}

	/** A vertex attribute by its name. */
	def attribute(name:String):FloatBuffer = {
		if(meshAttributes ne null) {
			meshAttributes.get(name) match {
				case Some(x) => x.data
				case None => null
			}
		} else {
			null
		}
	}

	/** A vertex attribute by its enumeration name. */
	def attribute(name:VertexAttribute.Value):FloatBuffer = attribute(name.toString)
 
    /** Number of vertex attributes defined. */
    def attributeCount():Int = {
    	if(meshAttributes ne null)
    		 meshAttributes.size
    	else 0
    }

    /** Name and order of all the vertex attributes defined by this mesh. */
    def attributes():Array[String] = {
    	if(meshAttributes ne null)
    		(meshAttributes.map { item => item._1 }).toArray
    	else {
     		return new Array(0)   		
    	}
    }

    /** True if this mesh has an attribute with the given `name`. */
    def hasAttribute(name:String):Boolean = if(meshAttributes ne null) meshAttributes.contains(name) else true
    // TODO remove the test, when all mesh will be compatible.

    /** Indices of the elements to draw in the attributes array, in draw order.
      * The indices points at elements in each attribute array. */
    def elements:IntBuffer = throw new InvalidPrimitiveException("no elements in this mesh")

    /** Number of components of the given vertex attribute. */
    def components(name:String):Int = {
    	if(meshAttributes ne null) {
    		meshAttributes.get(name) match {
    			case Some(x) => x.components
    			case None => throw new NoSuchVertexAttributeException("mesh has no attribute named %s".format(name))
    		}
    	} else {
			throw new NoSuchVertexAttributeException("mesh has no attribute named %s".format(name))
    	}
    }

    /** Number of components of the given vertex attribute. */
    def components(name:VertexAttribute.Value):Int = components(name.toString)

    /** True if the vertex attribute whose name is given is defined in this mesh. */
    def has(name:String):Boolean = {
    	if(meshAttributes ne null)
    		 meshAttributes.contains(name)
    	else false
    }

    /** True if the vertex attribute whose name is given is defined in this mesh. */
    def has(name:VertexAttribute.Value):Boolean = has(name.toString)
    
    /** True if the mesh has elements indices in the vertex attributes to define primitives. */
    def hasElements():Boolean = false

    /** How to draw the mesh (as points, lines, lines loops, triangles, etc.).
      * This depends on the way the data is defined. */
    def drawAs(gl:SGL):Int

    /** Draw the last vertex array created. If no vertex array has been created 
      * a `NoVertexArrayException` is thrown. This uses the `drawAs()` method to select
      * how to draw the mesh (triangles, points, etc.). */
    def draw(gl:SGL) {
    	if(va ne null)
    		va.draw(drawAs(gl)) 
    	else throw new NoVertexArrayException("create a vertex array before draw")
    }

    /** Draw the `count` first primitives the last vertex array created. A
      * primitive is a line or triangle for example, it depends on the kind of mesh.
      * If no vertex array has been created a `NoVertexArrayException` is thrown.
      * This uses the `drawAs()` method to select
      * how to draw the mesh (triangles, points, etc.), and the `elementsPerPrimitive`
      * to know how many elements (vertices, colors) makes up a primitive. */
    def draw(gl:SGL, count:Int) {
    	if(va ne null)
    		va.draw(drawAs(gl), count * elementsPerPrimitive)
    	else throw new NoVertexArrayException("create a vertex array before draw")
    }

    /** Draw `count` primitives of the last vertex array created starting at `start`. A
      * primitive is a line or triangle for example, it depends on the kind of mesh.
      * If no vertex array has been created a `NoVertexArrayException` is thrown.
      * This uses the `drawAs()` method to select
      * how to draw the mesh (triangles, points, etc.), and the `elementsPerPrimitive`
      * to know how many elements (vertices, colors) makes up a primitive. */
    def draw(gl:SGL, start:Int, count:Int) {
    	if(va ne null) {
    		val epp = elementsPerPrimitive
    		va.draw(drawAs(gl), start * epp, count * epp)
    	} else {
    		throw new NoVertexArrayException("create a vertex array before draw")
    	}
    }

    def drawInstanced(gl:SGL, instances:Int) {
    	if(va ne null) {
    		val epp = elementsPerPrimitive
    		va.drawInstanced(drawAs(gl), instances)
    	} else {
    		throw new NoVertexArrayException("create a vertex array before draw")
    	}
    }

    def drawInstanced(gl:SGL, count:Int, instances:Int) {
    	if(va ne null) {
    		val epp = elementsPerPrimitive
    		va.drawInstanced(drawAs(gl), count, instances)
    	} else {
    		throw new NoVertexArrayException("create a vertex array before draw")
    	}
    }

    def drawInstanced(gl:SGL, start:Int, count:Int, instances:Int) {
    	if(va ne null) {
    		val epp = elementsPerPrimitive
    		va.drawInstanced(drawAs(gl), start * epp, count * epp, instances)
    	} else {
    		throw new NoVertexArrayException("create a vertex array before draw")
    	}
    }
    
    override def toString():String = {
    	val attrs = attributes.map { item => (item, components(item)) }

    	"mesh(%s, attributes(%d) { %s })".format(
    		if(hasElements) "elements array" else "no elements array",
    		attributeCount,
    		attrs.mkString(", ")
    	)
    }

    /** The last created vertex array.
      *
      * Each time a vertex array is created with a mesh, it is remembered. Some
      * meshes allow to update the arrays when a change is made to the data in the
      * mesh. Such meshes are dynamic. */
    def vertexArray():VertexArray = va

    /** True if at least one vertex array was created. You can access it using `lastva()`. */
    def hasVertexArray:Boolean = (va ne null)

    /** The shader used to allocate the vertex array. */
    def shader:ShaderProgram = sh

    /** Always called before creating a new vertex array. Hook for sub-classes. */
    protected def beforeNewVertexArray() {}

    /** Always called after creating a new vertex array. Hook for sub-classes. */
    protected def afterNewVertexArray() {}

    /** Create a vertex array from the given `locations` map of attribute name to shader
      * attribute names. The given `shader` is directly used to query the position of
      * attribute names. The draw mode for the array buffers is STATIC_DRAW.
      * 
      * Example usage: 
      *
      *    newVertexArray(gl, myShader, "vertices" -> "V", "normals" -> "N")
      * 
      * If the shader contains input attribute named V and N. Example 2:
      *    
      *    import VertexAttribute._
      *    newVertexArray(gl, myShader, Vertex -> "V", Normal -> "N")
	  *
      * The last created vertex array is remembered by the mesh and can be accessed later,
      * and for some meshes updated from new data if the mesh is dynamic. */
    def newVertexArray(gl:SGL, shader:ShaderProgram, locations:Tuple2[String,String]*):VertexArray = newVertexArray(gl, gl.STATIC_DRAW, shader, locations:_*)

    /** Create a vertex array from the given `locations` map of attribute name to shader
      * attribute names. The given `shader` is directly used to query the position of
      * attribute names. You can specify the `drawMode` for the array buffers, either
      * STATIC_DRAW, STREAM_DRAW or DYNAMIC_DRAW.
      * 
      * Example usage: 
      *
      *    newVertexArray(gl, gl.STATIC_DRAW, myShader, "vertices" -> "V", "normals" -> "N")
      * 
      * If the shader contains input attribute named V and N. Example 2:
      *    
      *    import VertexAttribute._
      *    newVertexArray(gl, gl.STATIC_DRAW, myShader, Vertex -> "V", Normal -> "N")
	  *
      * The last created vertex array is remembered by the mesh and can be accessed later,
      * and for some meshes updated from new data if the mesh is dynamic. */
    def newVertexArray(gl:SGL, drawMode:Int, shader:ShaderProgram, locations:Tuple2[String,String]*):VertexArray = {
    	beforeNewVertexArray

    	val locs = new Array[Tuple4[String,Int,Int,NioBuffer]](locations.size)
    	var pos  = 0

    	locations.foreach { value => 
    		val attName = value._1
    		val varName = value._2
    		
    		if(!hasAttribute(attName))
    			throw new NoSuchVertexAttributeException("mesh has no attribute named '%s' (mapped to '%s')".format(attName, varName))

    		locs(pos) = (attName, shader.getAttribLocation(varName), components(attName), attribute(attName))
    		pos += 1
    	}

    	sh = shader
    	
    	if(hasElements)
    	     va = new VertexArray(gl, elements, drawMode, locs:_*)
    	else va = new VertexArray(gl, drawMode, locs:_*)

    	afterNewVertexArray

    	va
    }
}


/** Representation of a user-defined attribute.
  * 
  * Such attributes are set of floats (1 to 4, depending on the number of components),
  * each one being associated to a vertex. You can index the attribute by the
  * vertex number. Individual meshes have to allocate these
  * attribute by giving the number of vertices and the number of components per
  * vertice. This encapsulate a [[FloatBuffer]] to store data.
  *
  * This class also allows to memorize two vertex positions that mark the
  * begin and end of the area where modifcations have been done since the last
  * update (an update consists in sending data to OpenGL). These two markers
  * are update by `set()` method.
  *
  * The `set()` method allow to change individual
  * attribute values per vertex. However for efficiency reasons, as the creator
  * of sub-classes of this one, you have plain access to
  * the [[theData]] field wich is a buffer of [[vertexCount]]*[[components]]
  * floats, and [[beg]] and [[end]] markers, indicating the extent of modifications
  * in the buffer. If you mess with these fields, you known the consequences.
  * However this will be far faster than using the `set*()` methods. */
class MeshAttribute(val name:String, val components:Int, val vertexCount:Int) {
	
	var theData = FloatBuffer(vertexCount * components)

	var beg:Int = vertexCount

	var end:Int = 0

	/** Data under the form a float buffer. */
	def data:FloatBuffer = theData

	/** Change [[components]] values at `vertex` in the buffer.
	  * @param values must contain at least [[components]] elements.
	  * @param vertex an index as a vertex number. */
	def set(vertex:Int, values:Float*) {
		val i = vertex * components

		if(values.length >= components) {
			if(i >= 0 && i < theData.size) {
				if(beg > vertex)   beg = vertex
				if(end < vertex+1) end = vertex+1

				var j = 0

				while(j < components) {
					theData(i+j) = values(j)
					j += 1
				}
			} else {
				throw new InvalidVertexException(s"invalid vertex ${vertex} out of attribute buffer (size=${vertexCount})")
			}
		} else {
			throw new InvalidVertexComponentException(s"no enough values passed for attribute (${values.length}), needs ${components} components")
		}
	}

	/** Copy the data from the set of `values` given. The number of values must match the size of the attribute. */
	def copy(values:Float*) {
		if(values.length == vertexCount*components)
			theData.copy(values.asInstanceOf[scala.collection.mutable.WrappedArray[Float]].array)
		else throw new RuntimeException("use copy with exactly the correct number of arguments")
	}

	/** Update the buffer (send it to OpenGL) with the same name as this attribute if
	  * some elements have been changed. */
	def update(va:VertexArray) {
		if(end > beg) {
			if(beg==0 && end == vertexCount)
			     va.buffer(name).update(theData)
			else va.buffer(name).update(beg, end, theData)
			resetMarkers
		}
	}

	/** Used to reset the [[beg]] and [[end]] markers as if no changes
	  * have been made to the values. Used after `update()` and by the
	  * mesh before creating a new vertex array. */
	def resetMarkers() {
		beg = vertexCount
		end = 0
	}
}

/** Same role as [[MeshAttribute]] for the index of elements to draw primitives.
  * 
  * You are responsible for creating and maintaining an instance of this
  * class in sub-classes of [[Mesh]], this cannot be done automatically.
  *
  * @param primCount the number of primitives.
  * @param verticesPerPrim the number of vertices (or elements) for one primitive. */
class MeshElement(val primCount:Int, val verticesPerPrim:Int) {
	
	var theData = IntBuffer(primCount * verticesPerPrim)

	var beg:Int = primCount * verticesPerPrim

	var end:Int = 0

	/** Data under the form an int buffer. */
	def data:IntBuffer = theData

	def set(prim:Int, values:Int*) {
		val i = prim * verticesPerPrim

		if(values.length >= verticesPerPrim) {
			if(i >= 0 && i < theData.size) {
				if(beg > i) beg = i
				if(end < i+verticesPerPrim) end = i+verticesPerPrim

				var j = 0

				while(j < verticesPerPrim) {
					theData(i+j) = values(j)
					j += 1
				}
			} else {
				throw new InvalidPrimitiveException(s"invalid primitive index ${prim} out of index buffer (size=${primCount})")
			}
		} else {
			throw new InvalidPrimitiveVertexException(s"no enough values passed for primitive (${values.length}), needs ${verticesPerPrim} vertices indices")
		}
	}

	/** Copy the data from the set of `values` given. The number of values must match the size of the attribute. */
	def copy(values:Int*) {
		if(values.length == primCount*verticesPerPrim)
			theData.copy(values.asInstanceOf[scala.collection.mutable.WrappedArray[Int]].array)
		else throw new RuntimeException("use copy with exactly the correct number of arguments")
	}

	/** Update the buffer (send it to OpenGL) with the same name as this attribute if
	  * some elements have been changed. */
	def update(va:VertexArray) {
		if(end > beg) {
			if(beg==0 && end == primCount*verticesPerPrim)
			     va.indices.update(theData)
			else va.indices.update(beg, end, theData)
			resetMarkers
		}
	}

	/** Used to reset the [[beg]] and [[end]] markers as if no changes
	  * have been made to the values. Used after `update()` and by the
	  * mesh before creating a new vertex array. */
	def resetMarkers() {
		beg = primCount * verticesPerPrim
		end = 0
	}
}