package org.sofa.opengl.mesh


import javax.media.opengl._
import org.sofa.nio._
import org.sofa.opengl._
import org.sofa.math.{Point3, Rgba, Vector3}
import GL._
import GL2._
import GL2ES2._
import GL3._
import scala.math._


/** A hexagonal 2D grid. 
  *
  * A mesh of lines representing a grid of hexagonal cells. The grid is
  * axonometric and isometric. Each point not on the border of the grid
  * divides in three edges. Each of these edge is 30° (Pi/6 radians) of
  * each other.
  *
  * By default the cell edges are all of length 1 unit. This means that
  * a cell is 2 units in height and sqrt(3) in width.
  *
  * Althought the grid cells are not aligned vertically, we map the grid
  * on a 2D square grid for indicing. The above-at-right cell of a cell is
  * considered above vertically. 
  *
  * The first cell is at (0,0) and each cell is indexed in 2D as usual.
  * The center of this first cell is also at (0,0) in the user space. This
  * allows to easily find cells centers.
  *
  * Some ratios allow to change the grid while preserving its isometric
  * properties. The ratio parameter allows to grow the the grid.
  *
  * To give an illusion of perspective one can squeeze the
  * height of the vertical segments of cells. This is the perspective ratio.
  *
  * This mesh only support color attributes.
  */
class HexaGridMesh(
		val width            :Int,
		val height           :Int,
		val defaultColor     :Rgba   = Rgba.White,
		val ratio            :Double = 1.0,
		val perspectiveRatio :Double = 1.0
	) extends Mesh {

	protected val count = (((height * 2) + 2) * (width + 1))

	/** The mutable set of coordinates. */
    protected lazy val V = allocateVertices

	/** The mutable set of colors. */
    protected lazy val C = allocateColors

	/** The mutable set of elements to draw. */
    protected lazy val I = allocateIndices

    /** Start position of the last modification inside the index array. */
	protected var ibeg = 0
	
	/** End position of the last modification inside the index array. */
	protected var iend = 0
	
    /** Start position of the last modification inside the coordinates array. */
    protected var vbeg = count
    
    /** End position of the last modification inside the coordinates array. */
    protected var vend = 0
        
    /** Start position of the last modification inside the color array. */
    protected var cbeg = count
    
    /** End position of the last modification inside the color array. */
    protected var cend = 0

    // -- Mesh creation ------------------------------------------------

    protected def allocateVertices():FloatBuffer = {
    	// generate a set of points, organized first in rows (X) then
    	// in columns (Y).

    	val xunit = (sqrt(3) * ratio).toFloat 	// size of a cell along X.
    	val yunit = (ratio * 2).toFloat 		// size of a cell along Y.
    	val cols  = width + 1					// Number of columns of points.
    	val rows  = (height * 2) + 2 			// Number of rows of points.
    	val data  = FloatBuffer(rows * cols * 3)

    	var row = 0  							// Current row.
    	var col = 0 							// Current column.
    	var x   = 0f 							// Current x position for current point.
    	var y   = - yunit / 2f 					// Current y position for current point.

    	vbeg = 0
    	vend = 0

    	while(row < rows) {
    		val r4 = row % 4

    		if(r4 == 0 || r4 == 3) x = xunit/2 else x = 0f

    		while(col < cols) {
    			V(vend*3+0) = x
    			V(vend*3+1) = y
    			V(vend*3+2) = 0f

    			x    += xunit * 2
    			col  += 1
    			vend += 1
    		}

    		y   += (if(r4 == 0 || r4 == 2) yunit/4 else yunit/2)
    		row += 1
    	}

    	data
    }

    protected def allocateColors():FloatBuffer = {
    	val cols = width + 1
    	val rows = (height * 2) + 2
    	val size = rows * cols
		val data = FloatBuffer(size * 4)

		cend = 0

		while(cend < size) {
			C(cend*4+0) = defaultColor.red.toFloat
			C(cend*4+1) = defaultColor.green.toFloat
			C(cend*4+2) = defaultColor.blue.toFloat
			C(cend*4+3) = defaultColor.alpha.toFloat

			cend += 1
		}

		data
    }

    protected def allocateIndices():IntBuffer = {
    	val diag = width * 2 + 1
    	val vert = height + 1
    	val data = IntBuffer(diag * vert * 2)

    	// Allocate diagonals

    	var lines = height + 1
    	var line  = 0
    	var col   = 0
    	var pt    = 0

    	ibeg = 0
    	iend = 0

    	while(line < lines) {
    		col = 0
    		
    		while(col < (width+1)) {
    			I(iend+0) = pt + width + 1
		    	I(iend+1) = pt

		    	iend += 2
    			col  += 1
		    	pt   += 1
    		}

    		line += 1
    		pt   += width + 2
    	}

    	// Allocate verticals.

    	lines = height
    	line  = 0
    	pt    = width + 1

    	while(line < lines) {
    		col = 0

    		while(col < (width+1)) {
    			I(iend+0) = pt
    			I(iend+1) = pt + width + 1

    			iend += 2
    			col  += 1
    			pt   += 1
    		}

    		line += 1
    		pt += width + 2
    	}

    	// TODO

    	data
    }

    // -- Mesh Interface -----------------------------------------------
    
    def attribute(name:String):FloatBuffer = {
    	VertexAttribute.withName(name) match {
    		case VertexAttribute.Vertex => V
    		case VertexAttribute.Color  => C
    		case _                      => throw new RuntimeException("no %s attribute in this mesh".format(name))
    	}
    }

    def attributeCount():Int = 2

    def attributes():Array[String] = Array[String](VertexAttribute.Vertex.toString, VertexAttribute.Color.toString)
        
	override def indices:IntBuffer = I
		
	override def hasIndices = true

    def components(name:String):Int = {
    	VertexAttribute.withName(name) match {
    		case VertexAttribute.Vertex => 3
    		case VertexAttribute.Color  => 4
    		case _                      => throw new RuntimeException("no %s attribute in this mesh".format(name))
    	}

    }

    def has(name:String):Boolean = {
    	VertexAttribute.withName(name) match {
    		case VertexAttribute.Vertex => true
    		case VertexAttribute.Color  => true
    		case _                      => false
    	}
    }

    def drawAs():Int = GL_LINES

    // -- Edition -----------------------------------------------------

    /** Change the color of all the points of a cell given by its X and Y coordinates.
      *
      * Be careful that changing a cell points colors will affect neightbor cells since
      * points are shared.
      */
	def setCellColor(x:Int, y:Int, c:Rgba) { setCellColor(x, y, c.red.toFloat, c.green.toFloat, c.blue.toFloat, c.alpha.toFloat) }

    /** Change the color of all the points of a cell given by its X and Y coordinates.
      *
      * Be careful that changing a cell points colors will affect neightbor cells since
      * points are shared.
      */
	def setCellColor(x:Int, y:Int, r:Float, g:Float, b:Float, a:Float) {

	}

 //    def setColor(i:Int, ra:Float, ga:Float, ba:Float, aa:Float,
 //                        rb:Float, gb:Float, bb:Float, ab:Float) {
 //        val pos = i*4*2

 //        C(pos+0) = ra
 //        C(pos+1) = ga
 //        C(pos+2) = ba
 //        C(pos+3) = aa

 //        C(pos+4) = rb
 //        C(pos+5) = gb
 //        C(pos+6) = bb
 //        C(pos+7) = ab

 //        if(i < cbeg) cbeg = i
 //        if(i+1 > cend) cend = i+1
 //    }

    // -- Dynamic mesh --------------------------------------------------

    override def beforeNewVertexArray() {
        vbeg = count; vend = 0
	}

    /** Update the last vertex array created with newVertexArray(). Tries to update only what changed to
      * avoid moving data between the CPU and GPU. */
    def updateVertexArray(gl:SGL) { updateVertexArray(gl, true, true) }
     
    /** Update the last vertex array created with newVertexArray(). Tries to update only what changed to
      * avoid moving data between the CPU and GPU. */
    def updateVertexArray(gl:SGL, updateVertices:Boolean, updateColors:Boolean) {
        if(va ne null) {
            if(vend > vbeg) {
                if(vbeg == 0 && vend == count)
                     va.buffer(VertexAttribute.Vertex.toString).update(V)
                else va.buffer(VertexAttribute.Vertex.toString).update(vbeg, vend, V)
                
                vbeg = count
                vend = 0
            }
            if(cend > cbeg) {
                if(cbeg == 0 && cend == count)
                     va.buffer(VertexAttribute.Color.toString).update(C)
                else va.buffer(VertexAttribute.Color.toString).update(cbeg, cend, C)
                
                cbeg = count
                cend = 0                
            }
			if(iend > ibeg) {
				va.indices.update(ibeg, iend, I)
				ibeg = 1
				iend = 0
        	}
    	}
    }
}