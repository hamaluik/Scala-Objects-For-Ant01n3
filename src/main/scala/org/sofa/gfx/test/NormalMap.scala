package org.sofa.gfx.test

import scala.math._
import org.sofa.gfx._
import org.sofa.gfx.mesh._
import org.sofa.gfx.surface._
import org.sofa.nio._
import org.sofa.math._
import javax.media.opengl.GLProfile
import org.sofa.gfx.surface.event._

object NormalMap {
	def main(args:Array[String]):Unit = (new NormalMap)
}

class NormalMap extends SurfaceRenderer {
    var gl:SGL = null
    var surface:Surface = null
    
    val projection:Matrix4 = new Matrix4
    val modelview = new MatrixStack(new Matrix4)
    
    var nmapShader:ShaderProgram = null 
    
    val planeMesh = new PlaneMesh(2, 2, 4, 4)
    var plane:VertexArray = null
    val tubeMesh = new CylinderMesh(0.5f, 1, 16, 1)
    var tube:VertexArray = null
    
    var uvTex:Texture = null
    var specTex:Texture = null
    var nmapTex:Texture = null

    var camera:Camera = null
    var ctrl:BasicCameraController = null
    val clearColor = Rgba.Black
    val light1 = Vector4(2, 2, 2, 1)
    
    build
    
	def build() {
//	    val caps = new javax.media.opengl.GLCapabilities(GLProfile.get(GLProfile.GL3))
	    val caps = new javax.media.opengl.GLCapabilities(GLProfile.getGL2ES2)

		Texture.path += "textures/"	    
		Shader.path += "src/main/scala/org/sofa/gfx/shaders/"

	    caps.setDoubleBuffered(true)
	    caps.setHardwareAccelerated(true)
	    caps.setSampleBuffers(true)
	    caps.setRedBits(8)
	    caps.setGreenBits(8)
	    caps.setBlueBits(8)
	    caps.setAlphaBits(8)
	    caps.setNumSamples(4)

        camera         = Camera()
	    ctrl           = new MyCameraController(camera, light1)
	    initSurface    = initializeSurface
	    frame          = display
	    surfaceChanged = reshape
	    actionKey      = ctrl.actionKey
	    motion         = ctrl.motion
	    gesture        = ctrl.gesture
	    close          = { surface => sys.exit }
	    surface        = new org.sofa.gfx.backend.SurfaceNewt(this, camera, "Normal mapping", caps,
	    					org.sofa.gfx.backend.SurfaceNewtGLBackend.GL2ES2)
//	    					org.sofa.gfx.backend.SurfaceNewtGLBackend.GL3)
	}
	
	def initializeSurface(sgl:SGL, surface:Surface) {
	    gl = sgl
	    
	    gl.clearColor(clearColor)
	    gl.clearDepth(1f)
	    gl.enable(gl.DEPTH_TEST)
	    //gl.polygonMode(GL_FRONT_AND_BACK, GL_LINE)
	    
	    gl.enable(gl.CULL_FACE)
        gl.cullFace(gl.BACK)
        gl.frontFace(gl.CW)

	    setup(surface)
	}
	
	def setup(surface:Surface) {
		import VertexAttribute._

	    camera.eyeCartesian(2, 2, 2)
	    
	    nmapShader = new ShaderProgram(gl, "normal map phong",
	            new VertexShader(gl, "nmap phong", "es2/nmapPhong.vert"),
	            new FragmentShader(gl, "nmap phong", "es2/nmapPhong.frag"))
//	            new VertexShader(gl, "src-scala/org/sofa/gfx/shaders/nmapPhong.vert"),
//	            new FragmentShader(gl, "src-scala/org/sofa/gfx/shaders/nmapPhong.frag"))
	    
	    reshape(surface)
	    
	    tubeMesh.setTopDiskColor(Rgba.Yellow)
	    tubeMesh.setBottomDiskColor(Rgba.Yellow)
	    //tubeMesh.setDiskColor(4, Rgba.Red)
	    tubeMesh.setCylinderColor(Rgba.Blue);
	    planeMesh.setColor(Rgba.Magenta)
	    
	    plane = planeMesh.newVertexArray(gl, nmapShader, Vertex -> "position", Normal -> "normal", Tangent -> "tangent", TexCoord -> "texCoords")
	    tube  = tubeMesh.newVertexArray( gl, nmapShader, Vertex -> "position", Normal -> "normal", Tangent -> "tangent", TexCoord -> "texCoords")

	    uvTex = new Texture(gl, "color.png", TexParams(mipMap=TexMipMap.Generate))	    
//	    uvTex = new Texture(gl, "stone_wall__.jpg", true)
//	    uvTex = new Texture(gl, "textures/face.jpg", true)
	    uvTex.minMagFilter(gl.LINEAR_MIPMAP_LINEAR, gl.LINEAR)
	    uvTex.wrap(gl.REPEAT)

	    specTex = new Texture(gl, "specular.png", TexParams(mipMap=TexMipMap.Generate))
	    specTex.minMagFilter(gl.LINEAR_MIPMAP_LINEAR, gl.LINEAR)
	    specTex.wrap(gl.REPEAT)
	    
//	    nmapTex = new Texture(gl, "NormalFlat.png", false)
//	    nmapTex = new Texture(gl, "facenrm.jpg", true)
//	    nmapTex = new Texture(gl, "stone_wall_normal_map__.jpg", true)
		nmapTex = new Texture(gl, "normal.png", TexParams(mipMap=TexMipMap.Generate))
	    nmapTex.minMagFilter(gl.LINEAR_MIPMAP_LINEAR, gl.LINEAR)
	    nmapTex.wrap(gl.REPEAT)
	}
	
	def reshape(surface:Surface) {
	    camera.viewportPx(surface.width, surface.height)
        var ratio = camera.viewportRatio
        
        gl.viewport(0, 0, surface.width, surface.height)
        camera.frustum(-ratio, ratio, -1, 1, 1)
	}
	
	def display(surface:Surface) {
	    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
	    
	    nmapShader.use

	    camera.lookAt
	    
	    setupLights
	    setupTextures
	    
	    camera.uniform(nmapShader)
	    plane.draw(planeMesh.drawAs(gl))
/*	    tube.draw(tubeMesh.drawAs)

	    camera.pushpop {
		    camera.translateModel(0, 1.1, 0)
		    camera.uniformMVP(nmapShader)
		    tube.draw(tubeMesh.drawAs)
	    }
*/
	    camera.pushpop {
		    camera.translate(-1, 0, -1)
		    camera.uniform(nmapShader)
		    tube.draw(tubeMesh.drawAs(gl))
		    
		    camera.translate(0, 1.1, 0)
		    camera.uniform(nmapShader)
		    tube.draw(tubeMesh.drawAs(gl))
	    }
	    
	    //surface.swapBuffers
	}
	
	def setupLights() {
	    // We need to position the light by ourself, but avoid doing
	    // it at each pixel in the shader.
	    
	    nmapShader.uniform("lightPos", Vector3(camera.modelview.top * light1))
	    nmapShader.uniform("lightIntensity", 5f)
	    nmapShader.uniform("ambientIntensity", 0.1f)
	    nmapShader.uniform("specularPow", 16f)
	}
	
	def setupTextures() {
	    uvTex.bindTo(gl.TEXTURE0)
	    nmapShader.uniform("texColor", 0)	// Texture Unit 0
//	    specTex.bindTo(gl.TEXTURE1)
//	    nmapShader.uniform("texSpec", 1)	// Texture Unit 1
	    nmapTex.bindTo(gl.TEXTURE2)
	    nmapShader.uniform("texNormal", 2)	// Texture Unit 2
	}
}

class MyCameraController(camera:Camera, light:Vector4) extends BasicCameraController(camera) {
    override def actionKey(surface:Surface, keyEvent:ActionKeyEvent) {
println("TODO MyCameraController.actionKey (NormalMap)")
        // import org.sofa.gfx.surface.event.ActionKey._
        // if(keyEvent.isShiftDown) {
        //     if(! keyEvent.isPrintable) {
        //         keyEvent.actionChar match {
        //             case Up       => { light.x -= 0.1 }
        //             case Down     => { light.x += 0.1 }
        //             case Right    => { light.z -= 0.1 }
        //             case Left     => { light.z += 0.1 }
        //             case PageUp   => { light.y += 0.1 }
        //             case PageDown => { light.y -= 0.1 }
        //             case _ => {}
        //         }
        //     }
        // } else {
        //     super.key(surface, keyEvent)
        // }
    }
}