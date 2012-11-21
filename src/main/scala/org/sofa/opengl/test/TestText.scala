package org.sofa.opengl.test

import org.sofa.Timer
import org.sofa.opengl.surface.{SurfaceRenderer, Surface, BasicCameraController}
import org.sofa.opengl.{SGL, ShaderProgram, MatrixStack, VertexArray, Camera, Shader, TextureFramebuffer}
import org.sofa.opengl.mesh.{Plane, Cube, WireCube, Axis, DynPointsMesh}
import org.sofa.opengl.text.{GLFont}
import javax.media.opengl.{GLCapabilities, GLProfile}
import scala.collection.mutable.{ArrayBuffer, HashSet, Set}
import org.sofa.math.{SpatialPoint, SpatialCube, SpatialHash, SpatialObject, Point3, Vector3, Vector4, Rgba, Matrix4}

object TestText {
	def main(args:Array[String]) = (new TestText).test
}

class TestText extends SurfaceRenderer {
	var gl:SGL = null
	var surface:Surface = null
	
	val projection:Matrix4 = new Matrix4
	val modelview = new MatrixStack(new Matrix4)
	
	var textShad:ShaderProgram = null
	var plainShad:ShaderProgram = null
	
	var plane:VertexArray = null
	var axis:VertexArray = null
	
	var axisMesh = new Axis(10)
	var planeMesh = new Plane(2, 2, 4, 4)
	
	var camera:Camera = null
	var ctrl:BasicCameraController = null
	
	val clearColor = Rgba.white
	val planeColor = Rgba.grey80
	val light1 = Vector4(1, 2, 1, 1)

	var font:GLFont = null
		
	def test() {
		val caps = new GLCapabilities(GLProfile.getGL2ES2)
		
		caps.setRedBits(8)
		caps.setGreenBits(8)
		caps.setBlueBits(8)
		caps.setAlphaBits(8)
		caps.setNumSamples(4)
		caps.setDoubleBuffered(true)
		caps.setHardwareAccelerated(true)
		caps.setSampleBuffers(true)
		
		camera         = new Camera()
		ctrl           = new MyCameraController(camera, light1)
		initSurface    = initializeSurface
		frame          = display
		surfaceChanged = reshape
		key            = ctrl.key
		motion         = ctrl.motion
		scroll         = ctrl.scroll
		close          = { surface => sys.exit }
		surface        = new org.sofa.opengl.backend.SurfaceNewt(this,
							camera, "Test Text", caps,
							org.sofa.opengl.backend.SurfaceNewtGLBackend.GL2ES2)	
	}
	
	def initializeSurface(sgl:SGL, surface:Surface) {
		Shader.includePath += "/Users/antoine/Documents/Programs/SOFA/src/main/scala/org/sofa/opengl/shaders/"
			
		initGL(sgl)
		initShaders
		initGLText
		initGeometry
		
		camera.viewCartesian(5, 2, 5)
		camera.setFocus(0, 0, 0)
		reshape(surface)
	}
	
	protected def initGL(sgl:SGL) {
		gl = sgl
		
		gl.clearColor(clearColor)
		gl.clearDepth(1f)
		gl.enable(gl.DEPTH_TEST)
		gl.enable(gl.CULL_FACE)
		gl.cullFace(gl.BACK)
		gl.frontFace(gl.CW)
		gl.disable(gl.BLEND)
		gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)
		gl.enable(gl.PROGRAM_POINT_SIZE)	// Necessary on my ES2 implementation ?? 
	}
	
	def initShaders() {
		textShad = ShaderProgram(gl, "phong shader", "es2/text.vert.glsl", "es2/text.frag.glsl")
		plainShad = ShaderProgram(gl, "plain shader", "es2/plainColor.vert.glsl", "es2/plainColor.frag.glsl")
	}

	def initGLText() {
		GLFont.path += "/Users/antoine/Library/Fonts"

		font = new GLFont(gl, "DroidSerif-Italic.ttf", 22, 0, 0)
	}
	
	def initGeometry() {
		var v = textShad.getAttribLocation("position")
		var c = textShad.getAttribLocation("texCoords")

		planeMesh.setTextureRepeat(1,1)
		plane = planeMesh.newVertexArray(gl, ("vertices", v), ("texcoords", c))
		
		v = plainShad.getAttribLocation("position")
		c = plainShad.getAttribLocation("color")
		
		axis = axisMesh.newVertexArray(gl, ("vertices", v), ("colors", c))		
	}	

	def display(surface:Surface) {
		gl.viewport(0, 0, surface.width, surface.height)
		gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
		gl.frontFace(gl.CW)
		
		camera.setupView
		
		// Axis
		
		gl.enable(gl.BLEND)
		plainShad.use
		camera.setUniformMVP(plainShad)
		axis.draw(axisMesh.drawAs)

		// Plane
		
		camera.pushpop {
			val scale = (font.texture.width / 100.0)

			textShad.use
			font.texture.bindTo(gl.TEXTURE0)
	    	textShad.uniform("texColor", 0)	// Texture Unit 0
	    	textShad.uniform("textColor", Rgba.black)
	    	camera.scaleModel(scale, scale, scale)
			camera.setUniformMVP(textShad)
			plane.draw(planeMesh.drawAs)
			gl.bindTexture(gl.TEXTURE_2D, 0)
		}

		gl.disable(gl.BLEND)
		
		surface.swapBuffers
		gl.checkErrors
	}
	
	def reshape(surface:Surface) {
		camera.viewportPx(surface.width, surface.height)
		gl.viewport(0, 0, camera.viewportPx.x.toInt, camera.viewportPx.y.toInt)
		camera.frustum(-camera.viewportRatio, camera.viewportRatio, -1, 1, 2)
	}
	
	// protected def useLights(shader:ShaderProgram) {
	// 	shader.uniform("light.pos", Vector3(camera.modelview.top * light1))
	// 	shader.uniform("light.intensity", 4f)
	// 	shader.uniform("light.ambient", 0.1f)
	// 	shader.uniform("light.specular", 100f)
	// }
}