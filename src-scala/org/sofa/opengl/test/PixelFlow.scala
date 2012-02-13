package org.sofa.opengl.test

import com.jogamp.opengl.util._
import com.jogamp.newt.event._
import com.jogamp.newt.opengl._

import org.sofa.nio._
import org.sofa.opengl._

import javax.media.opengl.glu._
import javax.media.opengl._

import GL._
import GL2._
import GL2ES2._
import GL3._ 

object PixelFlow {
	def main(args:Array[String]):Unit = {
	    (new PixelFlow).test
	}
}

class PixelFlow extends WindowAdapter with GLEventListener {
    var gl:SGL = null
    var triangle:VertexArray = null
    var shadProg:ShaderProgram = null
    
    val vertices = FloatBuffer( 
        -0.8f, -0.8f, 0.0f, 1.0f,
         0.8f, -0.8f, 0.0f, 1.0f,
         0.8f,  0.8f, 0.0f, 1.0f,
        -0.8f,  0.8f, 0.0f, 1.0f)
         
    val colors = FloatBuffer(
        1.0f, 0.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f)
        
    val indices = IntBuffer(0, 1, 2, 0, 2, 3)
    
    val vertexShader = Array[String](
    		"#version 330\n",
    		"layout(location=0) in vec4 in_Position;\n",
    		"layout(location=1) in vec4 in_Color;\n",
    		"out vec4 ex_Color;\n",

    		"void main(void) {\n",
    		"	gl_Position = in_Position;\n",
    		"	ex_Color = in_Color;\n",
    		"}\n")
        
    val fragmentShader = Array[String](
    		"#version 330\n",
 
    		"in vec4 ex_Color;\n",
    		"out vec4 out_Color;\n",
 
    		"void main(void) {\n",
    		"	out_Color = ex_Color;\n",
    		"}\n")
    
    def test() {
        val prof = GLProfile.get(GLProfile.GL3)
        val caps = new GLCapabilities(prof)
    
        caps.setDoubleBuffered(true)
        caps.setRedBits(8)
        caps.setGreenBits(8)
        caps.setBlueBits(8)
        caps.setAlphaBits(8)
        
        val win = GLWindow.create(caps)
        val anim = new FPSAnimator(win , 60)

        win.addWindowListener(this)
        win.addGLEventListener(this)
        win.setSize(800, 600)
        win.setTitle("Basic OpenGL setup")
        win.setVisible(true)
        
        anim.start
    }
    
    override def windowDestroyNotify(ev:WindowEvent) { exit }
    
    def init(win:GLAutoDrawable) {
        gl = new SGL(win.getGL.getGL3, GLU.createGLU)
        
        gl.printInfos
        gl.clearColor(0f, 0f, 0f, 0f)
        gl.clearDepth(1f)
        gl.enable(GL_DEPTH_TEST)
    
        shadProg = new ShaderProgram(gl,
                new VertexShader(gl, vertexShader),
                new FragmentShader(gl, fragmentShader))

        triangle = new VertexArray(gl, indices, (4, vertices), (4, colors))
    }
    
    def reshape(win:GLAutoDrawable, x:Int, y:Int, width:Int, height:Int) {
        gl.viewport(0, 0, width, height)
    }
    
    def display(win:GLAutoDrawable) {
        gl.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
        shadProg.use
        triangle.drawTriangles
        win.swapBuffers
    }
    
    def dispose(win:GLAutoDrawable) {}
}