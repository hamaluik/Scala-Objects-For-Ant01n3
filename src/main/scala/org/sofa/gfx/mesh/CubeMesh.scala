package org.sofa.gfx.mesh

import org.sofa.nio._
import org.sofa.gfx._
import org.sofa.math.Rgba

/** A cube, centered around (0, 0, 0) whose `side` can be specified.
  * 
  * The vertex data must use indices. The cube is made of single triangles and therefore must be
  * drawn using "triangle" mode. Triangles are in CW order. 
  *
  * As with each [[Mesh]], you can use any vertex attribute you desire, but there are helper
  * methods to allocate colors, normals, tex-coords and tangents so that they are already
  * filled correctly. Use methods:
  *    - allocateTexCoords()
  *    - allocateNormals()
  *    - allocateColors()
  *    - allocateTangents()
  *
  * There is also a facility to use instanced rendering and draw multiples cubes with one call.
  */
class CubeMesh(val side:Float) extends Mesh {
    
    protected var I:MeshElement = allocateIndices
    protected var V:MeshAttribute = allocateVertices
    protected var C:MeshAttribute = _ 
    protected var N:MeshAttribute = _ 
    protected var X:MeshAttribute = _ 
    protected var T:MeshAttribute = _ 

    protected var textureRepeatS:Int = 1
    
    protected var textureRepeatT:Int = 1

	/** Define how many times the texture repeats along the S and T coordinates. This must be
	  * done before the plane is transformed to a mesh. */    
    def setTextureRepeat(S:Int, T:Int) {
        textureRepeatS = S
        textureRepeatT = T
    }

    /** Set the color of the whole cube. Automatically allocate a color vertex
      * attribute if needed. */
    def setColor(color:Rgba) {
    	if(C eq null)
    		allocateColors

    	val n = 6 * 4 * 4
    	val d = C.theData
    	
    	for(i <- 0 until n by 4) {
    		d(i+0) = color.red.toFloat
    		d(i+1) = color.green.toFloat
    		d(i+2) = color.blue.toFloat
    		d(i+3) = color.alpha.toFloat
    	}
    }

    // -- Mesh interface ---------------------------------------------

    def vertexCount:Int = 24

    def elementsPerPrimitive:Int = 3

    // override def attribute(name:String):FloatBuffer = {
    // 	VertexAttribute.withName(name) match {
    // 		case VertexAttribute.Vertex   => V
    // 		case VertexAttribute.Color    => C
    // 		case VertexAttribute.Normal   => N
    // 		case VertexAttribute.TexCoord => X
    // 		case VertexAttribute.Tangent  => T
    // 		case _                        => super.attribute(name) //throw new RuntimeException("mesh has no attribute %s".format(name))
    // 	}
    // }

    override def elements:IntBuffer = I.theData

    // override def attributeCount():Int = 5 + super.attributeCount

    // override def attributes():Array[String] = Array[String](VertexAttribute.Vertex.toString, VertexAttribute.Normal.toString,
    // 		VertexAttribute.Tangent.toString, VertexAttribute.TexCoord.toString, VertexAttribute.Color.toString) ++ super.attributes

    // override def components(name:String):Int = {
    // 	VertexAttribute.withName(name) match {
    // 		case VertexAttribute.Vertex   => 3
    // 		case VertexAttribute.Color    => 4
    // 		case VertexAttribute.Normal   => 3
    // 		case VertexAttribute.TexCoord => 2
    // 		case VertexAttribute.Tangent  => 3
    // 		case _                        => super.components(name)// throw new RuntimeException("mesh has no attribute %s".format(name))
    // 	}    	
    // }

	// override def has(name:String):Boolean = {
 //    	VertexAttribute.withName(name) match {
 //    		case VertexAttribute.Vertex   => true
 //    		case VertexAttribute.Color    => true
 //    		case VertexAttribute.Normal   => true
 //    		case VertexAttribute.TexCoord => true
 //    		case VertexAttribute.Tangent  => true
 //    		case _                        => super.has(name) //false
 //    	}
	// }

    override def hasElements():Boolean = true
    
    def drawAs(gl:SGL):Int = gl.TRIANGLES    

	// -- Building ---------------------------------------------------
    
    protected def allocateVertices:MeshAttribute = {
    	if(V eq null) {
	    	V = addMeshAttribute(VertexAttribute.Vertex, 3)
	        val s = side / 2f

	        V.copy(
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
        V
    }
    
    def allocateTexCoords:MeshAttribute = {
    	if(X eq null) {
	        X = addMeshAttribute(VertexAttribute.TexCoord, 2)
	        val s = textureRepeatS
	        val t = textureRepeatT

	        X.copy(
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
        
        X
    }

    def allocateColors:MeshAttribute = {
        if(C eq null) {
	        var i = 0
	        val n = 6 * 4 * 4
	        C = addMeshAttribute(VertexAttribute.Color, 4)// FloatBuffer(n)
	        val d = C.theData
	        
	        while(i < n) {
	        	d(i) = 1f
	        	i += 1
	        }
	    }

        C
    }

    def allocateNormals:MeshAttribute = {
    	if(N eq null) {
	        N = addMeshAttribute(VertexAttribute.Normal, 3)

	       	N.copy(
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

       	N
    }

    def allocateTangents:MeshAttribute = {
    	if(T eq null) {
	        T = addMeshAttribute(VertexAttribute.Tangent, 3)

	        T.copy(
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
        
        T
    }

    protected def allocateIndices:MeshElement = {
        if(I eq null) {
	        I = new MeshElement(12, 3)

	        I.copy(
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

        I
    }
}