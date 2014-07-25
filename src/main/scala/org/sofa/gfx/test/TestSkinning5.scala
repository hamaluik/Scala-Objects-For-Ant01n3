package org.sofa.gfx.test

import scala.math._
import javax.media.opengl._
import javax.media.opengl.glu._
import com.jogamp.opengl.util._
import com.jogamp.newt.event._
import com.jogamp.newt.opengl._

import org.sofa.nio._
import org.sofa.math.{Rgba, Vector3, Vector4}
import org.sofa.gfx.{SGL, Camera, VertexArray, ShaderProgram, Texture, Shader, WhiteLight, TexParams, TexMipMap}
import org.sofa.gfx.io.collada.{ColladaFile}
import org.sofa.gfx.surface.{Surface, SurfaceRenderer, BasicCameraController}
import org.sofa.gfx.mesh.{PlaneMesh, Mesh, BoneMesh, EditableMesh, VertexAttribute}
import org.sofa.gfx.mesh.skeleton._

object TestSkinning5 {
    def main(args:Array[String]):Unit = (new TestSkinning5).test
}

class TestSkinning5 extends SurfaceRenderer {
// General
    
    var gl:SGL = null
    var surface:Surface = null
	
// View
    
    var camera:Camera = null
    var ctrl:BasicCameraController = null
    
// Geometry

    var ground:VertexArray = null
    var thing:VertexArray = null
    var bone:VertexArray = null
    
    val groundMesh = new PlaneMesh(2, 2, 6 , 6)
    var thingMesh:Mesh = null
    val boneMesh = new BoneMesh()
    
    var skeleton:Bone = null
    
// Shading
    
    val clearColor = Rgba.Grey30
    
    var phongShader:ShaderProgram = null
    var nmapShader:ShaderProgram = null
    var plainShader:ShaderProgram = null
    var boneShader:ShaderProgram = null
    
    var light1 = WhiteLight(0, 2, 2,  5f, 16f, 0.1f)
    
    var groundColor:Texture = null
    var groundNMap:Texture = null
    var thingColor:Texture = null
    var thingNMap:Texture = null

// Go
        
    def test() {
	    camera   = Camera(); camera.viewportPx(1280,800)
	    val caps = new GLCapabilities(GLProfile.get(GLProfile.GL2ES2))
	    
		caps.setDoubleBuffered(true)
		caps.setHardwareAccelerated(true)
		caps.setSampleBuffers(true)
		caps.setNumSamples(4)

        ctrl           = new MyCameraController(camera, light1.pos)
	    initSurface    = initializeSurface
	    frame          = display
	    surfaceChanged = reshape
	    close          = { surface => sys.exit }
	    actionKey      = ctrl.actionKey
	    motion         = ctrl.motion
	    gesture        = ctrl.gesture
	    surface        = new org.sofa.gfx.backend.SurfaceNewt(this,
	    					camera, "Skinning", caps,
	    					org.sofa.gfx.backend.SurfaceNewtGLBackend.GL2ES2)
	}
    
// Rendering
    
	def initializeSurface(gl:SGL, surface:Surface) {
	    Shader.path      += "/Users/antoine/Documents/Programs/SOFA/src/main/scala/org/sofa/gfx/shaders/"
	    Shader.path      += "shaders/"
	    Texture.path     += "/Users/antoine/Documents/Programs/SOFA/textures"
	    Texture.path     += "textures/"
		ColladaFile.path += "/Users/antoine/Documents/Art/Sculptures/Blender/"
		ColladaFile.path += "meshes/"

	    initGL(gl)
	    initTextures
        initShaders
        initModels
	    initGeometry
	    
	    camera.eyeCartesian(0, 10, 10)
	    camera.setFocus(0, 2, 0)
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
	}
	
	protected def initShaders() {
	    plainShader = ShaderProgram(gl, "plain",       "es2/plainColor.vert.glsl",   "es2/plainColor.frag.glsl")
	    nmapShader  = ShaderProgram(gl, "phong n-map", "es2/phonghinmapw.vert.glsl", "es2/phonghinmapw.frag.glsl")
		boneShader  = ShaderProgram(gl, "basic bones", "es2/phongtexbone.vert.glsl", "es2/phongtexbone.frag.glsl")

	    boneShader.uniform("bone[0].color", Rgba(1, 0.1, 0, 1))
	    boneShader.uniform("bone[1].color", Rgba(1, 0.6, 0.1, 1))
	    boneShader.uniform("bone[2].color", Rgba(0.7, 0.2, 0.7, 1))
	    boneShader.uniform("bone[3].color", Rgba(0.3, 0.1, 1, 1))
	}

	protected def initModels() {
		val model = new ColladaFile("Bruce_005.dae")

		model.library.geometry("BirouteLowPoly").get.mesh.mergeVertices(true)
//		model.library.geometry("BirouteLowPoly").get.mesh.blenderToOpenGL(true)

		// Build a SOFA bone hierarchy and a SOFA mesh from the Collada data.

//		model.library.visualScene("Scene").get.blenderToOpenGL(true)

	    skeleton  = model.library.visualScene("Scene").get.toSkeleton("Armature", model.library.controller("Armature").get)
		thingMesh = model.library.geometry("BirouteLowPoly").get.mesh.toMesh

		// Ask the mesh to auto compute tangents that are sadly lacking from Collada export of Blender :'(

		thingMesh.asInstanceOf[EditableMesh].autoComputeTangents(true)
	}
	
	protected def initGeometry() {
		import VertexAttribute._

		groundMesh.setTextureRepeat(30, 30)

	    ground = groundMesh.newVertexArray(gl, nmapShader,  Vertex -> "position", Normal -> "normal", Tangent -> "tangent", TexCoord -> "texCoords")
	    thing  = thingMesh.newVertexArray( gl, boneShader,  Vertex -> "position", Normal -> "normal", Tangent -> "tangent", TexCoord -> "texCoords", Bone -> "boneIndex", Weight -> "boneWeight")
	    bone   = boneMesh.newVertexArray(  gl, plainShader, Vertex -> "position", Color -> "color")
	}
	
	protected def initTextures() {
	    groundColor = new Texture(gl, "textures/Ground.png", TexParams(mipMap=TexMipMap.Generate))
	    groundColor.minMagFilter(gl.LINEAR_MIPMAP_LINEAR, gl.LINEAR)
	    groundColor.wrap(gl.REPEAT)

	    groundNMap = new Texture(gl, "textures/GroundNMap.png", TexParams(mipMap=TexMipMap.Generate))
	    groundNMap.minMagFilter(gl.LINEAR_MIPMAP_LINEAR, gl.LINEAR)
	    groundNMap.wrap(gl.REPEAT)

	    thingColor = new Texture(gl, "textures/BruceColor.png", TexParams(mipMap=TexMipMap.Generate))
	    thingColor.minMagFilter(gl.LINEAR_MIPMAP_LINEAR, gl.LINEAR)
	    thingColor.wrap(gl.REPEAT)

	    thingNMap = new Texture(gl, "textures/BruceNMap.png", TexParams(mipMap=TexMipMap.Generate))
	    thingNMap.minMagFilter(gl.LINEAR_MIPMAP_LINEAR, gl.LINEAR)
	    thingNMap.wrap(gl.CLAMP_TO_EDGE)
	}
	
	def reshape(surface:Surface) {
	    camera.viewportPx(surface.width, surface.height)
        var ratio = camera.viewportRatio
        
        gl.viewport(0, 0, camera.viewportPx.x.toInt, camera.viewportPx.y.toInt)
        camera.frustum(-ratio, ratio, -1, 1, 2)
	}
	
	def display(surface:Surface) {
	    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
	    
	    camera.lookAt


		// Ground

	  	gl.enable(gl.BLEND)
	    nmapShader.use
	    light1.uniform(nmapShader, camera)
	    useTextures(nmapShader, groundColor, groundNMap)
	    camera.uniform(nmapShader)
	    ground.draw(groundMesh.drawAs(gl))
		gl.disable(gl.BLEND)

		animate

		gl.frontFace(gl.CCW)
	    boneShader.use
	    light1.uniform(boneShader, camera)
	    useTextures(boneShader, thingColor, thingNMap)
	    skeleton.uniform(boneShader)
	    camera.uniform(boneShader)
	    thing.draw(thingMesh.drawAs(gl))
		gl.frontFace(gl.CW)

 		// Skeleton

 		gl.disable(gl.DEPTH_TEST)
	    plainShader.use
	    skeleton.drawSkeleton(gl, camera, plainShader)
 		gl.enable(gl.DEPTH_TEST)
	    
	    // Ok

	    //surface.swapBuffers
	    gl.checkErrors
	}

	var join0 = 0.0
	var join1 = 0.0
	var join2 = 0.0
	var join1Scale = 1.0
	var join0Dir = +0.005
	var join1Dir = +0.005
	var join2Dir = +0.05
	var join1ScaleDir = +0.01

	var lightRadius = 2.0
	var lightDir = +0.01
	var lightx = 0.0
	var lighty = 2.0
	
	def animate() {
//	    join0 += join1Dir
	    join1 += join1Dir
	    join2 += join2Dir
	    
	    join1Scale += join1ScaleDir

//	    if(join0 > Pi/20 || join1 < -Pi/20) join0Dir = -join0Dir
	    if(join1 > Pi/4 || join1 < -Pi/4) join1Dir = -join1Dir
	    if(join2 > Pi/4 || join2 < -Pi/4) join2Dir = -join2Dir
	    if(join1Scale > 1.2 || join1Scale < 0.8) join1ScaleDir = -join1ScaleDir 

	    skeleton.identity
//	    skeleton.rotate(join0, 1, 0, 0)
	    skeleton(0).identity
	    skeleton(0).rotate(join1, 0, 0, 1)
//	    skeleton(0).scale(join1Scale, 1, join1Scale)
	    //skeleton(0).scale(0.7, 1, 0.7)
	    skeleton(0)(0).identity
	    skeleton(0)(0).rotate(join2, 1, 0, 0)
	    //skeleton(0)(0).scale(0.7, 1, 0.7)

	    lightx += lightDir

	    if(lightx > 2*Pi)
	    	lightx = 0.0

	    light1.pos.set(cos(lightx)*lightRadius, lighty, sin(lightx)*lightRadius)
	}
	
	def useTextures(shader:ShaderProgram, color:Texture, nmap:Texture) {
	   	color.bindTo(gl.TEXTURE0)
	    shader.uniform("texColor", 0)
	    nmap.bindTo(gl.TEXTURE1)
	    shader.uniform("texNormal", 1)
	}
}