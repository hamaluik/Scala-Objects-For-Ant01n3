package org.sofa.gfx.mesh

import org.sofa.gfx.SGL
import org.sofa.nio._

import scala.math._

/** Create a circle centered at (0,0,0) with given `radius` and number of `sides` on the XZ plane.
  * 
  * The circle is made of lines. 
  * 
  * The data is usable to draw directly the vertices or to use indices. Vertices are given in
  * order, following the trigonometric direction. They must be drawn in "line loop" mode.
  */
class CircleMesh(radius:Double, sides:Int) extends Mesh {
	
	protected lazy val V:FloatBuffer = allocateVertices
    
    protected lazy val I:IntBuffer = allocateIndices

    // -- Mesh interface -------------------------------

    def vertexCount:Int = sides

    def elementsPerPrimitive:Int = sides

	override def attribute(name:String):FloatBuffer = {
		VertexAttribute.withName(name) match {
			case VertexAttribute.Vertex => V
			case _                      => super.attribute(name) //throw new RuntimeException("mesh has no %s attribute".format(name))
		}
	}

	override def attributeCount():Int = 1 + super.attributeCount

	override def attributes():Array[String] = Array[String](VertexAttribute.Vertex.toString) ++ super.attributes
    
    override def indices:IntBuffer = I

    override def hasIndices = true

	override def components(name:String):Int = {
		VertexAttribute.withName(name) match {
			case VertexAttribute.Vertex => 3
			case _                      => super.components(name)// throw new RuntimeException("mesh has no %s attribute".format(name))
		}
	}

	override def has(name:String):Boolean = {
		VertexAttribute.withName(name) match {
			case VertexAttribute.Vertex => true
			case _                      => super.has(name) //false
		}
	}
    
    override def drawAs(gl:SGL):Int = gl.LINE_LOOP
    
    // -- Building -------------------------------------

    protected def allocateVertices():FloatBuffer = {
	    var size = (sides) * 3
	    val buf = FloatBuffer(size)
	    val kstep = (2*Pi) / sides
	    var k = 0.0
	    
	    for(i <- 0 until size by 3) {
	        buf(i)   = (cos(k) * radius).toFloat
	        buf(i+1) = 0f
	        buf(i+2) = (sin(k) * radius).toFloat
	        k += kstep
	    }
	    
	    buf
	}
	
	protected def allocateIndices():IntBuffer = {
	    val size = sides
	    val buf = IntBuffer(size)
	    
	    for(i <- 0 until size) {
	        buf(i) = i
	    }
	    
	    buf
	}
}