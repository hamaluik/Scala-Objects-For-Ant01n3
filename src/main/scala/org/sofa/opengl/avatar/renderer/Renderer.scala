package org.sofa.opengl.avatar.renderer

import scala.math._
import scala.collection.mutable.HashMap

import javax.media.opengl._
import javax.media.opengl.glu._
import com.jogamp.opengl.util._
import com.jogamp.newt.event._
import com.jogamp.newt.opengl._

import org.sofa.nio._
import org.sofa.math.{Rgba, Vector3, Vector4, Axes, Point3, NumberSeq3}
import org.sofa.opengl.{SGL, Camera, VertexArray, ShaderProgram, Texture, Shader, HemisphereLight, ResourceDescriptor, Libraries}
import org.sofa.opengl.io.collada.{ColladaFile}
import org.sofa.opengl.surface.{Surface, SurfaceRenderer, BasicCameraController, MotionEvent, KeyEvent, ScrollEvent}
import org.sofa.opengl.mesh.{PlaneMesh, Mesh, BoneMesh, EditableMesh, VertexAttribute}
import org.sofa.opengl.mesh.skeleton.{Bone ⇒ SkelBone}

import akka.actor.{Actor, Props, ActorSystem, ReceiveTimeout, ActorRef}
import scala.concurrent.duration._
import org.sofa.opengl.akka.SurfaceExecutorService

import org.sofa.opengl.avatar.test.{GameActor}


case class RendererException(msg:String) extends Exception(msg)


object Renderer { def apply(controler:ActorRef):Renderer = new RendererNewt(controler) }


/** The real rendering service. This manages the communication with
  * the OpenGL surface and renders each screen and their set of avatars
  * and animate them.
  *
  * This renderer is maintained by a [[RendererActor]].
  *
  * The `controler` is an actor that will receive events from the renderer and
  * renderer actor.
  *
  * A renderer can be seen as a set of screens. It has one current screen (or none),
  * and can switch between them. Only one screen is visible at a time. */
abstract class Renderer(val controler:ActorRef) extends SurfaceRenderer {
// General

	/** OpenGL. */    
    var gl:SGL = null

    /** Frame buffer. */
    var surface:Surface = null

	/** Set of screens. */
	val screens = new HashMap[String,Screen]()

	/** Current screen. */
	var screen:Screen = null
   
// == Resources ==============================

	/** The set of shaders. */
	var libraries:Libraries = null

// == Init. ==================================
        
    /** Initialize the surface with its size, and optionnal title (on desktops). Once finished, we have an OpenGL context and a
      * frame buffer. The `fps` parameter tells the renderer internal animation loop will try to draw as much frames per second.
      * This internal loop is responsible for running the avatars and screens animate methods and redrawing the screen.
      *
      * This method is called when building the renderer actor, see [[RendererActor.apply()]].
      *
      * @param title The title of the window if windowed.
      * @param initialWidth The requested width in pixels of the surface.
      * @param initialHeight The requested height in pixels of the surface.
      * @param fps The requested frames-per-second, the renderer will try to match this.
      * @param decorated If false, no system borders are drawn around the window (if in a windowed system).
      * @param fullscreen If in a windowed system, try to open a full screen surface.
      * @param overSample Defaults to 4, if set to 1 or less, oversampling is disabled. */
    def start(title:String, initialWidth:Int, initialHeight:Int, fps:Int,
              decorated:Boolean=false, fullscreen:Boolean=false, overSample:Int = 4) {
	    initSurface    = initRenderer
	    frame          = render
	    surfaceChanged = reshape
	    close          = { surface ⇒ controler ! GameActor.Exit }
	    key            = onKey
	    motion         = onMotion
	    scroll         = onScroll
	    surface        = newSurface(this, initialWidth, initialHeight, title, fps,
	    							decorated, fullscreen, overSample)
	}

	/** Only method to implement in descendant classes to create a surface that suits the system. */
	protected def newSurface(renderer:SurfaceRenderer, width:Int, height:Int, title:String, fps:Int, decorated:Boolean, fullscreen:Boolean, overSample:Int):Surface

	/** The current screen or a NoSuchScreenException if no screen is current. */
	def currentScreen():Screen = {
		if(screen ne null) {
			screen
		} else {
			throw NoSuchScreenException("renderer has no current screen")
		}
	}

// == Surface events ===========================

	def onScroll(surface:Surface, e:ScrollEvent) { if(screen ne null) { screen.pinch(e.amount) } }

	def onKey(surface:Surface, e:KeyEvent) {}
	
	def onMotion(surface:Surface, e:MotionEvent) { if(screen ne null) { screen.motion(e) } }

// == Rendering ================================
    
	def initRenderer(gl:SGL, surface:Surface) { this.gl = gl; libraries = Libraries(gl) }
	
	def reshape(surface:Surface) { if(screen ne null) { screen.reshape } }
	
	def render(surface:Surface) {
		animate

		if(screen ne null) {
			screen.render
		} else {
			gl.clearColor(Rgba.Black)
			gl.clear(gl.COLOR_BUFFER_BIT)
		}

	    surface.swapBuffers
	    gl.checkErrors
	}

	def animate() { if(screen ne null) { screen.animate } }

// == Screens ====================================

	def addScreen(name:String, screen:Screen) {
		if(!screens.contains(name)) {
			screens += (name -> screen)
		} else {
			throw RendererException("Cannot add screen %s, already present".format(name))
		}
	}

	def removeScreen(name:String) {
		val s = screens.get(name).getOrElse(throw NoSuchScreenException("cannot remove non existing screen %s".format(name)))
		
		if(s ne screen) {
			screens -= s.name
		} else {
			throw RendererException("cannot remove screen %s since it is current".format(name))
		}
	}

	def switchToScreen(name:String) {
		if(screen ne null) { screen.end }
		
		screen = screens.get(name).getOrElse(null)

		if(screen ne null) { screen.begin }
	}

// == Utility ===================================

	/** Transforms a Size into a triplet of values. */
	def toTriplet(sz:Size):NumberSeq3 = {
		sz match {
			case fromTex:SizeFromTextureWidth ⇒ {
				val tex = libraries.textures.get(gl, fromTex.fromTexture)
				Vector3(fromTex.scale, fromTex.scale / tex.ratio, 0.01)
			}
			case fromTex:SizeFromTextureHeight ⇒ {
				val tex = libraries.textures.get(gl, fromTex.fromTexture)
				Vector3(fromTex.scale * tex.ratio, fromTex.scale, 0.01)
			}
			case fromScreen:SizeFromScreenWidth ⇒ {
				if(screen ne null) {
					val tex = libraries.textures.get(gl, fromScreen.fromTexture)
					Vector3((fromScreen.scale * screen.width), (fromScreen.scale * screen.width) / tex.ratio, 0.01)
				} else {
					throw NoSuchScreenException("cannot use SizeFromScreenWidth since there is no current screen")
				}
			}
			case fromScreen:SizeFromScreenHeight ⇒ {
				if(screen ne null) {
					val tex = libraries.textures.get(gl, fromScreen.fromTexture)
					Vector3((fromScreen.scale * screen.height) * tex.ratio, (fromScreen.scale * screen.height), 0.01)
				} else {
					throw NoSuchScreenException("cannot use SizeFromScreenHeight since there is no current screen")
				}
			}
			case triplet:SizeTriplet ⇒ { Vector3(triplet.x, triplet.y, triplet.z) }
		}
	}
}


/** A renderer class for the Jogl NEWT system. It creates an OpenGL ES 2.0 context. */
class RendererNewt(controller:ActorRef) extends Renderer(controller) {
	protected def newSurface(renderer:SurfaceRenderer, width:Int, height:Int, title:String, fps:Int, decorated:Boolean, fullscreen:Boolean, overSample:Int):Surface = {
	    val caps = new GLCapabilities(GLProfile.get(GLProfile.GL2ES2))

		caps.setDoubleBuffered(true)
		caps.setHardwareAccelerated(true)
		caps.setSampleBuffers(overSample > 1)
		caps.setNumSamples(overSample)

	    new org.sofa.opengl.backend.SurfaceNewt(this,
	    		width, height, title, caps,
	    		org.sofa.opengl.backend.SurfaceNewtGLBackend.GL2ES2,
	    		fps, decorated, fullscreen)
	}
}