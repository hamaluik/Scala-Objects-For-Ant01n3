package org.sofa.opengl.mesh

import org.sofa.nio._
import org.sofa.opengl._
import org.sofa.math.Rgba
import javax.media.opengl._
import GL._
import GL2._
import GL2ES2._
import GL3._

/** A cube, centered around (0, 0, 0) whose `side` can be specified.
  * 
  * The vertex data must use indices. The cube is made of single triangles and therefore must be
  * drawn using "triangle" mode. You can use normals, and colors with this mesh.
  * 
  * Triangles are in CW order.
  */
class Cube(val side:Float)
	extends Mesh with ColorableMesh with IndexedMesh
	with SurfaceMesh with TexturableMesh with TangentSurfaceMesh {
    
    protected lazy val V:FloatBuffer = allocateVertices
    protected lazy val C:FloatBuffer = allocateColors
    protected lazy val N:FloatBuffer = allocateNormals
    protected lazy val I:IntBuffer = allocateIndices
    protected lazy val X:FloatBuffer = allocateTexCoords
    protected lazy val T:FloatBuffer = allocateTangents

    protected var textureRepeatS:Int = 1
    protected var textureRepeatT:Int = 1

    def vertices:FloatBuffer = V
    override def colors:FloatBuffer = C
    override def normals:FloatBuffer = N
    override def indices:IntBuffer = I
    override def texCoords:FloatBuffer = X
    override def tangents:FloatBuffer = T
        
    override def hasColors = true

    override def hasIndices = true
    
    override def hasNormals = true
    
    override def hasTexCoords = true
    
    override def hasTangents = true
    
    protected def allocateVertices:FloatBuffer = {
        val s = side / 2f
        
        FloatBuffer(
        // Front
        -s, -s,  s,			// 0
         s, -s,  s,			// 1
         s,  s,  s,			// 2
        -s,  s,  s,			// 3
        // Right
         s, -s,  s,			// 4
         s, -s, -s,			// 5
         s,  s, -s,			// 6
         s,  s,  s,			// 7
        // Back
         s, -s, -s,			// 8
        -s, -s, -s,			// 9
        -s,  s, -s,		 	// 10
         s,  s, -s,			// 11
        // Left
        -s, -s, -s,			// 12
        -s, -s,  s,			// 13
        -s,  s,  s,			// 14
        -s,  s, -s,			// 15
        // Top
        -s,  s,  s,			// 16
         s,  s,  s,			// 17
         s,  s, -s,			// 18
        -s,  s, -s,			// 19
        // Bottom
        -s, -s, -s,			// 20
         s, -s, -s,			// 21
         s, -s,  s,			// 22
        -s, -s,  s)			// 23

    }
    
    protected def allocateTexCoords:FloatBuffer = {
        val s = textureRepeatS
        val t = textureRepeatT
        
        FloatBuffer(
        	// Front
            0, 0,
        	s, 0,
        	s, t,
        	0, t,
        	// Right
        	0, 0,
        	s, 0,
        	s, t,
        	0, t,
        	// Back
        	0, 0,
        	s, 0,
        	s, t,
        	0, t,
        	// Left
        	0, 0,
        	s, 0,
        	s, t,
        	0, t,
        	// Top
        	0, 0,
        	s, 0,
        	s, t,
        	0, t,
        	// Bottom
        	0, 0,
        	s, 0,
        	s, t,
        	0, t
        )
    }

    protected def allocateColors:FloatBuffer = {
        val n   = 6 * 4 * 4
        val buf = new FloatBuffer(n)
        
        for(i <- 0 until n) {
        	buf(i) = 1f
        }
        
        buf
    }
    
    /** Set the color of each face. */
    def setColor(color:Rgba) {
    	val n = 6 * 4 * 4
    	
    	for(i <- 0 until n by 4) {
    		C(i+0) = color.red.toFloat
    		C(i+1) = color.green.toFloat
    		C(i+2) = color.blue.toFloat
    		C(i+3) = color.alpha.toFloat
    	}
    }

    protected def allocateNormals:FloatBuffer = {
        FloatBuffer(
    	// Front
         0,  0,  1,
         0,  0,  1,
         0,  0,  1,
         0,  0,  1,
    	// Right
         1,  0,  0,
         1,  0,  0,
         1,  0,  0,
         1,  0,  0,
    	// Back
         0,  0, -1,
         0,  0, -1,
         0,  0, -1,
         0,  0, -1,
    	// Left
        -1,  0,  0,
        -1,  0,  0,
        -1,  0,  0,
        -1,  0,  0,
    	// Top
         0,  1,  0,
         0,  1,  0,
         0,  1,  0,
         0,  1,  0,
    	// Bottom
         0, -1,  0,
         0, -1,  0,
         0, -1,  0,
         0, -1,  0)
    }

    protected def allocateTangents:FloatBuffer = {
        FloatBuffer(
    	// Front
         1,  0,  0,
         1,  0,  0,
         1,  0,  0,
         1,  0,  0,
    	// Right
         0,  0, -1,
         0,  0, -1,
         0,  0, -1,
         0,  0, -1,
    	// Back
        -1,  0,  0,
        -1,  0,  0,
        -1,  0,  0,
        -1,  0,  0,
    	// Left
         0,  0,  1,
         0,  0,  1,
         0,  0,  1,
         0,  0,  1,
    	// Top
         1,  0,  0,
         1,  0,  0,
         1,  0,  0,
         1,  0,  0,
    	// Bottom
        -1,  0,  0,
        -1,  0,  0,
        -1,  0,  0,
        -1,  0,  0)
    }

    protected def allocateIndices:IntBuffer = {
        IntBuffer(
        // Front
        0, 2, 1,
        0, 3, 2,
        // Right
        4, 6, 5,
        4, 7, 6,
        // Back
        8, 10, 9,
        8, 11, 10,
        // Left
        12, 14, 13,
        12, 15, 14,
        // Top
        16, 18, 17,
        16, 19, 18,
        // Bottom
        20, 22, 21,
        20, 23, 22)
    }
    
    def drawAs:Int = GL_TRIANGLES    
}