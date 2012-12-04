package org.sofa.opengl.mesh

import scala.collection.mutable.{ArrayBuffer, HashMap}
import org.sofa.nio.{FloatBuffer, IntBuffer}
import org.sofa.math.{Rgba, NumberSeq3, NumberSeq2, Point3, Vector3, Point2}

import javax.media.opengl.GL._
import javax.media.opengl.GL2._

case class BadlyNestedBeginEnd(msg:String) extends Throwable(msg) {
	def this() { this("Badly nested begin()/end()") }
}

object MeshDrawMode extends Enumeration {
	val TRIANGLES = Value(GL_TRIANGLES)
	val QUADS     = Value(GL_QUADS)
}

/** A mesh class that mimics the way OpenGL 1 builds geometry.
  *
  * TODO document this, actually only TRIANGLES are completely supported (notably for
  * the automatic construction of tangents and biTangents). */
class EditableMesh extends Mesh {
	import MeshDrawMode._
	
	/** Primitives to draw. */
	protected var drawMode = TRIANGLES
	
	/** How to draw the primitives. */
	protected var indexBuffer:ArrayBuffer[Int] = null
	
	/** Main vertex attributes, positions. */
	protected val vertexBuffer = new MeshBuffer("vertex", 3)
	
	/** Optional vertex attributes. */
	protected val otherBuffers:HashMap[String,MeshBuffer] = new HashMap[String,MeshBuffer]()
	
	/** Allow to check begin/end nesting. */
	protected var beganVertex = false
	
	/** Allow to chech beginIndices/endIndices nesting. */
	protected var beganIndex = false
	
	// --------------------------------------------------------------
	// Access
	
	override def drawAs():Int = drawMode.id
	
	// --------------------------------------------------------------
	// Command, mesh building

	/** Start to build the vertex attributes. 
	  * The draw mode for the primitive must be given here. Any previous values are deleted. */
	def begin() {
		if(beganVertex) throw new BadlyNestedBeginEnd("cannot nest begin() calls");
		
		otherBuffers.clear
		vertexBuffer.clear
		nioCache.clear

		
		beganVertex = true
	}

	/** Same as calling begin(MeshDrawMode), a code that calls vertex, color, normal, etc. and a final call to en(). */
	def buildAttributes(code: => Unit) {
		begin
		code
		end
	}
	
	/** Add a vertex to the primitive. If other attributes were declared but not changed
	  * the vertex take the last value specified for these attributes. */
	def vertex(x:Float, y:Float, z:Float) {
		if(!beganVertex) throw new BadlyNestedBeginEnd
		vertexBuffer.append(x, y, z)
		otherBuffers.foreach { _._2.sync(vertexBuffer) }
	}
	
	/** Specify the color for the next vertex or vertices. */
	def color(red:Float, green:Float, blue:Float, alpha:Float) { attribute("color", red, green, blue, alpha) }
	
	/** Specify the color for the next vertex or vertices. */
	def color(red:Float, green:Float, blue:Float) { color(red, green, blue, 1) }

	/** Specify the color for the next vertex or vertices. */
	def color(rgba:Rgba) { color(rgba.red.toFloat, rgba.green.toFloat, rgba.blue.toFloat, rgba.alpha.toFloat) }
	
	/** Specify the normal for the next vertex or vertices. */
	def normal(x:Float, y:Float, z:Float) { attribute("normal", x, y, z) }

	/** Specify the normal for the next vertex or vertices. */
	def normal(n:NumberSeq3) { normal(n.x.toFloat, n.y.toFloat, n.z.toFloat) }
	
	/** Specify the tangent for the next vertex or vertices. */
	def tangent(x:Float, y:Float, z:Float) { attribute("tangent", x, y, z) }
	
	/** Specify the tangent for the next vertex or vertices. */
	def tangent(t:NumberSeq3) { tangent(t.x.toFloat, t.y.toFloat, t.z.toFloat) }

	/** Specify the texture coordinates for the next vertex or vertices. */
	def texCoord(u:Float, v:Float) { attribute("texcoord", u, v) }

	/** Specify the texture coordinates for the next vertex or vertices. */
	def texCoord(uv:NumberSeq2) { texCoord(uv.x.toFloat, uv.y.toFloat) }
	
	/** Specify the bone index for the next vertex or vertices. */
	def bone(b:Int) { attribute("bone", b) }
	
	/** Specify an arbitrary attribute for the next vertex or vertices. */
	def attribute(name:String, values:Float*) {
		if(!beganVertex) throw new BadlyNestedBeginEnd

		// Get the corresponding buffer, or create it if needed.

		val buffer = otherBuffers.getOrElseUpdate(name, { new MeshBuffer(name, values.size, vertexBuffer, values:_*) })
		
		// We append only if the vertex buffer has the same number of elements.

		if(buffer.elements == vertexBuffer.elements) {
			buffer.append(values:_*)
		} else {
			throw new RuntimeException("cannot append attribute %s, add vertices first".format(name))
		}
	}
	
	/** End the building of the primitive. Any new call the begin() will
	  * erase the previous primitive. You can then call methods to create
	  * (or update) a vertex array from the primitives. */
	def end() {
		if(!beganVertex) throw new BadlyNestedBeginEnd
		beganVertex = false
	}
	
	/** The setup of primitives from the vertex attributes.
	  * The draw mode tells how he vertex attributes are used to draw primitives. */
	def beginIndices(drawMode:MeshDrawMode.Value) {
		if(beganIndex) throw new BadlyNestedBeginEnd("cannot nest beginIndices() calls");
		
		this.drawMode = drawMode

		beganIndex = true
		indexNioCache = null
		indexBuffer = new ArrayBuffer[Int]()
	}

	/** Like a call to beginIndices(), calls to index(Int) inside the given code and a call to endIndices(). */
	def buildIndices(drawMode:MeshDrawMode.Value)(code: => Unit) {
		beginIndices(drawMode)
		code
		endIndices
	}
	
	/** Specify the index of a vertex while building a triangle, [[beginIndices()]] must have been
	  * called, or this must be invoked in a code block passed to [[buildIndices(MeshDrawMode.Value)]]. */
	def index(i:Int) {
		if(!beganIndex) throw new BadlyNestedBeginEnd
		indexBuffer += i
	}
	
	/** End the definitions of the triangles. */
	def endIndices() {
		if(!beganIndex) throw new BadlyNestedBeginEnd
		beganIndex = false
	}

	/** Compute the relations between each vertex to each triangle. This allows, knowing a vertex,
	  * to iterate on the triangles it is connected to. Complexity O(n) with n triangles.
	  * Returns an array where each cell represents a vertex (in the same order as the vertex
	  * position attribute buffer), and contains an array of the triangles indices that uses
	  * this vertex. */
	def vertexToTriangle():Array[ArrayBuffer[Int]] = {
		// TODO we could do this at construction time ?! Memory vs. speed ...

		// Some verifications...
		
		if(beganIndex || beganVertex) throw new BadlyNestedBeginEnd("cannot call vertexToTriangle() inside begin/end")
		if(drawMode != MeshDrawMode.TRIANGLES) throw new RuntimeException("vertexToTriangle() works only on triangles actually")
		if(vertexBuffer.elements <= 0) throw new RuntimeException("vertexToTriangle() needs some vertices to operate upon")
		if(indexBuffer.size <= 0) throw new RuntimeException("vertexToTriangle() needs some indices to operate upon")

		// Create an array of int of the same number of elements as the vertexBuffer.

		val toTriangles = new Array[ArrayBuffer[Int]](vertexBuffer.elements)

		// For each triangle.

		val n = indexBuffer.size
		var i = 0

		while(i < n) {
			val p0 = indexBuffer(i)
			val p1 = indexBuffer(i+1)
			val p2 = indexBuffer(i+2)
			val t  = i / 3

//			println("Triangle %d references points %d, %d and %d".format(t, p0, p1, p2))

			if(toTriangles(p0) eq null) toTriangles(p0) = new ArrayBuffer[Int]()
			if(toTriangles(p1) eq null) toTriangles(p1) = new ArrayBuffer[Int]()
			if(toTriangles(p2) eq null) toTriangles(p2) = new ArrayBuffer[Int]()

			// Reference this triangle.

			toTriangles(p0) += t
			toTriangles(p1) += t
			toTriangles(p2) += t

			i += 3
		}

		i = 0

		// toTriangles.foreach { vertex =>
		// 	println("vertex %d references { %s }".format(i, vertex.mkString(", ")))
		// 	i += 1
		// }

		toTriangles
	}

	/** Recompute the tangents (and eventually normals).
	  * The mesh must already have the triangles indices, the vertices position, normals, and texture coordinates, all
	  * four are needed to build the tangents. */
	def autoComputeTangents() = autoComputeTangents(false)

	/** Recompute the tangents (and eventually normals).
	  * The mesh must already have the triangles indices, the vertices position, normals, and texture coordinates, all
	  * four are needed to build the tangents.
	  * The process involves to compute biTangents, they can be stored as a vertex attribute also by passing
	  * true as argument. */
	def autoComputeTangents(alsoComputeBiTangents:Boolean) {
		// Do some verifications.

		if(beganIndex || beganVertex) throw new BadlyNestedBeginEnd("cannot call autoComputeTangents() inside begin/end")
		if(drawMode != MeshDrawMode.TRIANGLES) throw new RuntimeException("autoComputeTangents() works only on triangles actually")
		if(vertexBuffer.elements <= 0) throw new RuntimeException("autoComputeTangents() needs some vertices to operate upon")
		if(indexBuffer.size <= 0) throw new RuntimeException("autoComputeTangents() needs some indices to operate upon")
		val texcooBuffer = otherBuffers.get("texcoord").getOrElse(throw new RuntimeException("autoComputeTangents() needs some texture coordinates to operate upon"))
		if(texcooBuffer.elements <= 0) throw new RuntimeException("autoComputeTangents() needs some texture coordinates to operate upon")
		var normalBuffer = otherBuffers.get("normal").getOrElse(throw new RuntimeException("autoComputeTangents() needs some normals to operate upon"))
		if(normalBuffer.elements <= 0) throw new RuntimeException("autoComputeTangents() needs some normals to operate upon")

		// Add (or replace) a tangent buffer.

		otherBuffers += (("tangent", new MeshBuffer("tangent", 3, null)))
		val tangentBuffer = otherBuffers.get("tangent").get

		var biTangentBuffer:MeshBuffer = null

		if(alsoComputeBiTangents) {
			otherBuffers += (("bitangent", new MeshBuffer("bitangent", 3, null)))
			biTangentBuffer = otherBuffers.get("bitangent").get
		}

		// Compute the relations between triangles and points.

		val toTriangles = vertexToTriangle

		// Pfew, now we can build the tangents ...

		var i = 0
		val n = vertexBuffer.elements; assert(n == toTriangles.size)

		while(i < n) {	// for each vertex
			var T = toTriangles(i)(0)		// <- Actually assume the vertex is shared by no other triangle !!
			var p0 = indexBuffer(T*3)		//    we do not "average" all the triangles...
			var p1 = indexBuffer(T*3+1)
			var p2 = indexBuffer(T*3+2)

			if(p0 != i) {
				// Make sur p0 references our vertex.
				if(p1 == i) { val tmp = p0; p0 = p1; p1 = tmp; }
				else if(p2 == i) { val tmp = p0; p0 = p2; p2 = tmp; }
				else throw new RuntimeException("WTF?! i=%d p0=%d p1=%d p2=%d".format(i, p0, p1, p2))
			}

			val P0 = Point3(vertexBuffer(p0*3), vertexBuffer(p0*3+1), vertexBuffer(p0*3+2))
			val P1 = Point3(vertexBuffer(p1*3), vertexBuffer(p1*3+1), vertexBuffer(p1*3+2))
			val P2 = Point3(vertexBuffer(p2*3), vertexBuffer(p2*3+1), vertexBuffer(p2*3+2))

			val UV0 = Point2(texcooBuffer(p0*2), texcooBuffer(p0*2+1))
			val UV1 = Point2(texcooBuffer(p1*2), texcooBuffer(p1*2+1))
			val UV2 = Point2(texcooBuffer(p2*2), texcooBuffer(p2*2+1))

			val x1 = P1.x - P0.x
			val y1 = P1.y - P0.y
			val z1 = P1.z - P0.z
			
			val x2 = P2.x - P0.x
			val y2 = P2.y - P0.y
			val z2 = P2.z - P0.z
			
			val s1 = UV1.x - UV0.x
			val t1 = UV1.y - UV0.y
			
			val s2 = UV2.x - UV0.x
			val t2 = UV2.y - UV0.y

			var d    = (s1 * t2 - s2 * t1); assert(d != 0)
			val r    = 1.0f / d
        	val sdir = Vector3((t2 * x1 - t1 * x2) * r, (t2 * y1 - t1 * y2) * r, (t2 * z1 - t1 * z2) * r)
        	val tdir = Vector3((s1 * x2 - s2 * x1) * r, (s1 * y2 - s2 * y1) * r, (s1 * z2 - s2 * z1) * r)

        	// Orthogonalize the tangent with the normal (Gram-Schmidt).
        	//tangent[a] = (t - n * Dot(n, t)).Normalize();
        
        	val N = Vector3(normalBuffer(i*3), normalBuffer(i*3+1), normalBuffer(i*3+2))
        	N.multBy(N.dot(sdir))
        	sdir.subBy(N)
        	sdir.normalize

        	// Finally add the tangent, one for each vertex.

        	tangentBuffer.append(sdir.x.toFloat, sdir.y.toFloat, sdir.z.toFloat)

        	if(alsoComputeBiTangents)
 				biTangentBuffer.append(tdir.x.toFloat, tdir.y.toFloat, tdir.z.toFloat)       	

        	i += 1
		}

		assert(tangentBuffer.elements == vertexBuffer.elements)
		assert(tangentBuffer.elements == normalBuffer.elements)
	}

	/** Create a new mesh (a ColoredLineMesh) that represents the normals (in red)
	  * and the tangents (in green). The mesh contains position, and color attributes
	  * only. It must be drawn using single lines. This allows to verify the normals and
	  * tangents are good. */
	def newNormalsTangentsMesh():Mesh = {
		var point    = 0
		val vertices = vertexBuffer
		val normals  = getNormalMeshBuffer
		val tangents = getTangentMeshBuffer
		val n        = vertices.elements
		val ntMesh   = new ColoredLineSet(n*2)

		while(point < n) {
			val P = Point3(vertices(point*3), vertices(point*3+1), vertices(point*3+2))
			val N = Vector3(normals(point*3), normals(point*3+1), normals(point*3+2))
			val T = Vector3(tangents(point*3), tangents(point*3+1), tangents(point*3+2))

			ntMesh.setColor(point, Rgba.red)
			ntMesh.setLine(point, P, N)
			ntMesh.setColor(point+n, Rgba.green)
			ntMesh.setLine(point+n, P, T)

			point += 1
		}

		ntMesh
	}

	// --------------------------------------------------------------
	// Access.

	/** The internal storage for the vertex position attributes. */
	def getVertexMeshBuffer():MeshBuffer = vertexBuffer

	/** The internal storage for the vertex normal attributes (as many as vertices). It may be null. */
	def getNormalMeshBuffer():MeshBuffer = otherBuffers.get("normal").getOrElse(null)

	/** The internal storage for the vertex tangent attributes (as many as vertices). It may be null.
	  * You can compute them automatically from the texture coordinates and normals, see
	  * [[autoComputeTangents()]]. */
	def getTangentMeshBuffer():MeshBuffer = otherBuffers.get("tangent").getOrElse(null)

	/** The internal storage for the vertex color attributes (as many as vertices). It may be null. */
	def getColorMeshBuffer():MeshBuffer = otherBuffers.get("color").getOrElse(null)

	/** The internal storage for the vertex texture coordinate attributes (as many as vertices). It may be null. */
	def getTexCoordMeshBuffer():MeshBuffer = otherBuffers.get("texcoord").getOrElse(null)

	// -----------------------------------------------------------------
	// Mesh interface.

	/** Cache of NIO float buffers. */
	protected val nioCache:HashMap[String,FloatBuffer] = new HashMap[String,FloatBuffer]()
    
	protected var indexNioCache:IntBuffer = null
	
    protected def getCache(name:String):FloatBuffer = nioCache.getOrElseUpdate(name,
    		{ new FloatBuffer(otherBuffers.get(name).getOrElse(throw new RuntimeException("no %s attributes in this mesh".format(name))).buffer) } )
	
	def vertices:FloatBuffer = nioCache.getOrElseUpdate("vertex", { new FloatBuffer(vertexBuffer.buffer) } )

    override def normals:FloatBuffer = getCache("normal")

    override def tangents:FloatBuffer = getCache("tangent")

    override def colors:FloatBuffer = getCache("color")

    override def texCoords:FloatBuffer = getCache("texcoord")
 
    override def bones:IntBuffer = throw new RuntimeException("TODO")//getCache("bone")

    override def indices:IntBuffer = {
		if(indexBuffer ne null) {
			if(indexNioCache eq null)
				indexNioCache = new IntBuffer(indexBuffer)
			indexNioCache 
		} else {
			throw new RuntimeException("no indices for this mesh") 
		}
    }

    override def hasNormals:Boolean = otherBuffers.contains("normal")
    
    override def hasTangents:Boolean = otherBuffers.contains("tangent")
    
    override def hasColors:Boolean = otherBuffers.contains("color")
    
    override def hasTexCoords:Boolean = otherBuffers.contains("texcoord")

    override def hasBones:Boolean = otherBuffers.contains("bone")
    
    override def hasIndices:Boolean = (indexBuffer ne null)
}

/** A buffer of elements each made of `components` float values, represents a vertex attribute set.
  * 
  * Therefore there are components*elements float values in the buffer.
  *
  * Such buffers are an easy way to store the data of each vertex
  * attribute, knowing how many components there are per vertex. It also allows
  * to "synchronize" an attribute with the vertex position, that is, if a vertex (position) is added
  * and the other vertex attributes are not set for the new vertex, all the other attributes have
  * their last entry duplicated for the new vertex. This mimics the way OpenGL works by setting for
  * example a color or a normal for several vertices.  */
protected class MeshBuffer(val name:String, val components:Int, other:MeshBuffer, values:Float*) {
	// --------------------------------------------------------------
	// Attributes
	
	/** The buffer. */
	val buffer = new ArrayBuffer[Float]
	
	/** Number of elements in the buffer (tuples of components values). */
	var elements = 0
	
	/** Temporary set of values (one element). */
	val temp = new Array[Float](components)
	
	// --------------------------------------------------------------
	// Construction

	/** New empty buffer. */
	def this(name:String, comps:Int) { this(name, comps, null)  }
	
	syncWithValues(other, values:_*)
	
	/** Fill the buffer by appending the given values (see `append()`), until this
	  * buffer has as many elements as the `other` buffer. Most of the time, this
	  * other buffer is the vertex positions attribute. If other is null, nothing
	  * is done. As this method is called in the constructor this allows to
	  * build a mesh buffer without initial values. */
	protected def syncWithValues(other:MeshBuffer, values:Float*) {
		if(other ne null) {
			while(elements < other.elements) {
				append(values:_*)
				elements += 1
			}
		}
	}

	// --------------------------------------------------------------
	// Access

	/** i-th element. */
	def apply(i:Int):Float = buffer(i)
	
	// --------------------------------------------------------------
	// Commands

	/** Remove all the elements. */
	def clear() {
		elements = 0
		buffer.clear
	}

	/** Iterate over each elements passing as many components */
	def foreach(code:(Float*) => Unit) {
		var i = 0
		val n = buffer.size

		while(i < n) {
			if(components == 1) {
				code(buffer(i))
			} else if(components == 2) {
				code(buffer(i), buffer(i+1))
			} else if(components == 3) {
				code(buffer(i), buffer(i+1), buffer(i+2))
			} else if(components == 4) {
				code(buffer(i), buffer(i+1), buffer(i+2), buffer(i+3))
			} else {
				throw new RuntimeException("does not handle components > 4 or < 1...")
			}

			i += components
		}
	}
	
	/** Fill the buffer with the last components added until it has a number of elements
	  * identical with the `other`. Most of the time `other` will be the vertex positions
	  * attribute buffer. This naturally works only if the buffer contains at leas one
	  * element. */
	def sync(other:MeshBuffer) {
		val n = ((elements-1) * components)
		var i = 0
		
		while(i < components) {
			temp(i) = buffer(n+i); i += 1
		}
		
		while(elements < other.elements) {
			i = 0
			while(i < components) {
				buffer += temp(i); i += 1
			}
			elements += 1
		}
	}
	
	/** Append a set of values. This always appends exactly the number of values
	  * given by `components`. If there are no enough `values`, zeroes are appended.
	  * If there are too many `values` they are ignored. */
	def append(values:Float*) {
		var i = 0
		while(i < components) {
			buffer += (if(values.size > i) values(i) else 0)
			i += 1
		}
		elements += 1
	}
	
	override def toString():String = "Buffer(%s: %d elt, %d comps)".format(name, elements, components)
}