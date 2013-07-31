package org.sofa.opengl

import java.io.IOException

import scala.collection.mutable.HashMap

import org.sofa.FileLoader
import org.sofa.opengl.text.{GLFont, GLString}
import org.sofa.opengl.mesh.{Mesh, PlaneMesh, CubeMesh, WireCubeMesh, AxisMesh, LinesMesh, VertexAttribute}
import org.sofa.opengl.armature.{Armature, Joint}
import org.sofa.opengl.armature.behavior.{ArmatureBehavior, LerpToAngle, LerpToPosition, LerpMove, InParallel, InSequence, Loop, Switch, LerpKeyArmature}

import scala.xml.{XML, Elem, Node, NodeSeq}


/** When a resource cannot be loaded. */
case class NoSuchResourceException(msg:String,nested:Throwable=null) extends Exception(msg,nested)


/** A resource. */
abstract class ResourceDescriptor[+T](val name:String) {
	/** The resource (lazily load it when needed). Throws NoSuchResourceException
	  * if the resource cannot be loaded. */
	def value(gl:SGL):T
}


/** A set of resources of a given type. */
abstract class Library[T](val gl:SGL) extends Iterable[(String,ResourceDescriptor[T])] {
	/** Set of loaded resources. */
	protected val library = new HashMap[String,ResourceDescriptor[T]]

	/** Add a new resource to the library. If `load` is true, the resource is 
	  * loaded when added, else it is loaded lazily at first access to its value. */
	def add(newResource:ResourceDescriptor[T], load:Boolean = false) { library += (newResource.name -> newResource); if(load) newResource.value(gl) }

	/** Add a new resource to the library. If `load` is true, the resource is 
	  * loaded when added, else it is loaded lazily at first access to its value. */
	def +=(newResource:ResourceDescriptor[T], load:Boolean = false) { add(newResource, load) }

	/** Load or retrieve a resource. */
	def get(gl:SGL, name:String):T = library.get(name).getOrElse(
			throw new NoSuchResourceException("resource %s unknown, did you put it in the Library ? use Library.add()".format(name))
		).value(gl)

	/** Remove an free a previously loaded resource. */
	def forget(name:String) { library -= name }

	def iterator = new Iterator[(String,ResourceDescriptor[T])] {
		var i = library.iterator
		def hasNext = i.hasNext
		def next = i.next
	}
}


// == Libraries ==========================================


object Libraries {
	final val PositionExpression = """\s*\(\s*([+-]?\d+(\.?\d*))\s*,\s*([+-?]\d+(\.?\d*))\s*\)\s*""".r

	def apply(gl:SGL):Libraries = { new Libraries(gl) } 
}

/** The set of libraries for shaders, textures and models. */
class Libraries(gl:SGL) {
	/** Shader resources. */
	val shaders = ShaderLibrary(gl)

	/** Textures resources. */
	val textures = TextureLibrary(gl)

	/** Model resources. */
	val models = ModelLibrary(gl)

	/** Font resources. */
	val fonts = FontLibrary(gl)

	/** Armature resources. */
	val armatures = ArmatureLibrary(gl)

	/** Behavior resources. */
	val behaviors = BehaviorLibrary(gl)

	/** Add a new resource in the corresponding library. */
	def addResource(res:ResourceDescriptor[AnyRef]) {
		res match {
			case r:ShaderResource   ⇒ shaders.add(r)
			case r:TextureResource  ⇒ textures.add(r)
			case r:ModelResource    ⇒ models.add(r)
			case r:FontResource     ⇒ fonts.add(r)
			case r:ArmatureResource ⇒ armatures.add(r)
			case _ ⇒ throw NoSuchResourceException("unknown kind of resource %s".format(res))
		}
	}

	/** Add a new resource in the corresponding library. */
	def +=(res:ResourceDescriptor[AnyRef]) { addResource(res) }

	/** Add resources and pathes under the form of an XML file.
	  * The file is searched in the classpath using the class loader
	  * resources. For example if it is at the root of the class path,
	  * put a / in front of the file name. */
	def addResources(fileName:String) { addResources(XML.load(getClass.getResource(fileName))) }

	/** Add resources and pathes under the form of an XML file.
	  *
	  * The format is the following:
	  *   
	  *		<resources>
	  *			<pathes>
	  *				<shader>path/to/shaders</shader>
	  *				<tex>path/to/texturess</tex>
	  *				<armature>path/to/armatures</armature>
	  *				<tex>there/can/be/several/pathes</tex>
	  *             <behavior>path/to/sifz/behavior/descriptions</behavior>
	  *			</pathes>
	  *			<shaders>
	  *				<shader id="mandatoryId" vert="vertexShader" frag="fragmentShader"/>
	  *			</shader>
	  *			<tex>
	  *				<tex id="mandatoryId" res="resource"/>
	  *				<tex id="anId" res="aResource" mipmap="sameAsTexMipMap" minfilter="" magfilter="" alpha="" wrap=""/>
	  *			</texs>
	  *         <armatures>
	  *             <armature id="man" tex="man-tex" shader="man-shader" svg="man-svg"/>
	  *         </armatures>
	  *         <behaviors>
	  *             <in-parallel      id="" arm="" behaviors=",,,,"/>
	  *				<in-sequence      id="" arm="" behaviors=",,,,"/>
	  *             <loop             id="" arm="" limit="" behaviors=",,,,"/>
	  *             <switch           id="" arm="" joints=",,,," duration=""/>
	  *             <lerp-to-angle    id="" arm="" joint="" value="" duration=""/>
	  *             <lerp-to-position id="" arm="" joint="" value="(,)" duration=""/>
	  *             <lerp-move        id="" arm="" joint="" value="(,)", duration=""/>
	  *             <lerp-keys        id="" arm="" filename="" scale=""/>
	  *         </behaviors>
	  *		</resources>
	  *
	  * See the [[TexParams]] class for an explanation of the attributes of tex elements. */
	def addResources(xml:Elem) {
		parsePathes(   xml \\ "pathes")
		parseShaders(  xml \\ "shaders")
		parseTexs(     xml \\ "texs")
		parseArmatures(xml \\ "armatures")
		parseBehaviors(xml \\ "behaviors")
	}

	protected def parsePathes(nodes:NodeSeq) {
		nodes \\ "shader"   foreach { Shader.path           += _.text }		
		nodes \\ "tex"      foreach { Texture.path          += _.text }
		nodes \\ "armature" foreach { Armature.path         += _.text }
		nodes \\ "behavior" foreach { ArmatureBehavior.path += _.text }
	}

	protected def parseShaders(nodes:NodeSeq) {
		nodes \\ "shader" foreach { shader ⇒
			shaders.add(ShaderResource(
				(shader \\ "@id").text,
				(shader \\ "@vert").text,
				(shader \\ "@frag").text))
		}
	}

	protected def parseTexs(nodes:NodeSeq) {
		nodes \\ "tex" foreach { tex ⇒
			val id  = (tex \\ "@id").text
			val res = (tex \\ "@res").text
			val params = TexParams(
				mipMap = (tex \\ "@mipmap").text.toLowerCase match {
						case "load"     ⇒ TexMipMap.Load
						case "generate" ⇒ TexMipMap.Generate
						case _          ⇒ TexMipMap.No
					},
				minFilter = (tex \\ "@minfilter").text.toLowerCase match {
						case "nearest"                 ⇒ TexMin.Nearest
						case "nearestandmipmapnearest" ⇒ TexMin.NearestAndMipMapNearest
						case "linearandmipmapnearest"  ⇒ TexMin.LinearAndMipMapNearest
						case "nearestandmipmaplinear"  ⇒ TexMin.NearestAndMipMapLinear
						case "linearandmipmaplinear"   ⇒ TexMin.LinearAndMipMapLinear
						case _                         ⇒ TexMin.Linear
					},
				magFilter = (tex \\ "@magfilter").text.toLowerCase match {
						case "nearest" ⇒ TexMag.Nearest
						case _         ⇒ TexMag.Linear
					},
				alpha = (tex \\ "@alpha").text.toLowerCase match {
						case "premultiply" ⇒ TexAlpha.Premultiply
						case _             ⇒ TexAlpha.Nop
					},
				wrap = (tex \\ "@wrap").text.toLowerCase match {
						case "clamp"          ⇒ TexWrap.Clamp
						case "mirroredrepeat" ⇒ TexWrap.MirroredRepeat
						case _                ⇒ TexWrap.Repeat
					}
			)

			textures.add(TextureResource(id, res, params))
		}
	}

	protected def parseArmatures(nodes:NodeSeq) {
		nodes \\ "armature" foreach { armature ⇒
			armatures.add(ArmatureResource(
				(armature \\ "@id").text,
				(armature \\ "@tex").text,
				(armature \\ "@shader").text,
				(armature \\ "@svg").text))
		}
	}

	protected def parseBehaviors(nodes:NodeSeq) {
	  	nodes foreach { node =>
		  	node.child foreach { child => 
		  		val armature = armatures.get(gl, (child \\ "@arm").text)
		  		val name     = (node \\ "@id").text
		  		
		  		child match {
		  			case node:Node if node.label == "in-parallel" => {
		  				behaviors += BehaviorResource(name, InParallel(
		  					name, parseArray((node \\ "@behaviors").text):_*))
		  			}
		  			case node:Node if node.label == "in-sequence" => {
		  				behaviors += BehaviorResource(name, InSequence(
		  					name, parseArray((node \\ "@behaviors").text):_*))
		  			}
		  			case node:Node if node.label == "loop" => {
		  				behaviors += BehaviorResource(name, Loop(
		  					name, (node \\ "@limit").text.toInt, parseArray((node \\ "@behaviors").text):_*))
		  			}
		  			case node:Node if node.label == "switch" => {
		  				behaviors += BehaviorResource(name, Switch(
		  					name, (node \\ "@duration").text.toLong, parseJoints(armature, (node \\ "@joints").text):_*))
		  			}
		  			case node:Node if node.label == "lerp-to-angle" => {
		  				behaviors += BehaviorResource(name, LerpToAngle(
		  					name, armature \\ (node \\ "@joint").text,
		  					(node \\ "@value").text.toDouble, (node \\ "@duration").text.toLong))
		  			}
		  			case node:Node if node.label == "lerp-to-position" => {
		  				behaviors += BehaviorResource(name, LerpToPosition(
		  					name, armature \\ (node \\ "@joint").text,
		  					parsePosition((node \\ "@value").text), (node \\ "@duration").text.toLong))
		  			}
		  			case node:Node if node.label == "lerp-move" => {
		  				behaviors += BehaviorResource(name, LerpMove(
		  					name, armature \\ (node \\ "@joint").text,
		  					parsePosition((node \\ "@value").text), (node \\ "@duration").text.toLong))
		  			}
		  			case node:Node if node.label == "lerp-keys" => {
		  				behaviors += BehaviorResource(name, LerpKeyArmature(
		  					name, armature, (node \\ "@filename").text, (node \\ "@scale").text.toDouble))
		  			}
		  			case _ => {}
	  			}
	  		}
	  	}
	}

	protected def parsePosition(pos:String):(Double,Double) = {
		import Libraries._
		pos match {
			case PositionExpression(a,b) => (a.toDouble,b.toDouble) 
			case _ => throw new RuntimeException("cannot parse position '%s'".format(pos))
		}
	}

	protected def parseArray(behaviorList:String):Array[ArmatureBehavior] = {
		behaviorList.split(",").map(s => behaviors.get(gl,s.trim))
	}

	protected def parseJoints(armature:Armature, jointList:String):Array[Joint] = {
		jointList.split(",").map(s => armature \\ s.trim)
	}
}


// == Shaders ============================================


object ShaderResource { def apply(name:String, vertex:String, fragment:String):ShaderResource = new ShaderResource(name, vertex, fragment) }

class ShaderResource(name:String, val vertex:String, val fragment:String) extends ResourceDescriptor[ShaderProgram](name) {
	private[this] var data:ShaderProgram = null

	def value(gl:SGL):ShaderProgram = {
		if(data eq null) {
			try {
				data = ShaderProgram(gl, name, vertex, fragment)
			} catch {
				case e:Exception ⇒ throw NoSuchResourceException(e.getMessage, e)
			}
		}

		data
	}
}

object ShaderLibrary { def apply(gl:SGL):ShaderLibrary = new ShaderLibrary(gl) }

class ShaderLibrary(gl:SGL) extends Library[ShaderProgram](gl)


// == Textures ============================================


object TextureResource { def apply(name:String,fileName:String,params:TexParams):TextureResource = new TextureResource(name, fileName, params) }

class TextureResource(
	name:String,
	val fileName:String,
	val params:TexParams)
		extends ResourceDescriptor[Texture](name) {
	
	private[this] var data:Texture = null

	def this(name:String, fileName:String) { this(name, fileName, TexParams()) }

	def value(gl:SGL):Texture = {
		if(data eq null) {
			try {
				data = new Texture(gl, fileName, params)
			} catch {
				case e:IOException ⇒ throw NoSuchResourceException(e.getMessage, e)
			}
		}

		data
	}
}

object TextureLibrary { def apply(gl:SGL):TextureLibrary = new TextureLibrary(gl) }

class TextureLibrary(gl:SGL) extends Library[Texture](gl)


// == Models ============================================


object ModelResource { def apply(name:String,fileName:String):ModelResource = new ModelResource(name,fileName) }

class ModelResource(name:String, val fileName:String) extends ResourceDescriptor[Mesh](name) {
	private var data:Mesh = null

	def value(gl:SGL):Mesh = {
		throw NoSuchResourceException("TODO")
	}
}

object ModelLibrary { def apply(gl:SGL):ModelLibrary = new ModelLibrary(gl) }

class ModelLibrary(gl:SGL) extends Library[Mesh](gl)


// == Fonts ============================================


object FontResource { def apply(name:String,fontName:String,size:Int):FontResource = new FontResource(name,fontName,size) }

class FontResource(name:String, val fontName:String, val size:Int) extends ResourceDescriptor[GLFont](name) {
	private var data:GLFont = null

	def value(gl:SGL):GLFont = {
		throw NoSuchResourceException("TODO")
	}
}

object FontLibrary { def apply(gl:SGL):FontLibrary = new FontLibrary(gl) }

class FontLibrary(gl:SGL) extends Library[GLFont](gl)


// == Armatures ========================================


object ArmatureResource {
	def apply(name:String, texRes:String, shaderRes:String, fileName:String):ArmatureResource = {
		new ArmatureResource(name, texRes, shaderRes, fileName)
	}
}

class ArmatureResource(name:String, texRes:String, shaderRes:String, fileName:String, private var data:Armature) extends ResourceDescriptor[Armature](name) {

	def this(name:String, texRes:String, shaderRes:String, fileName:String) {
		this(name, texRes, shaderRes, fileName, null)
	}

	def this(name:String, armature:Armature) {
		this(name, armature.texResource, armature.shaderResource, null, armature)
	}

	def value(gl:SGL):Armature = {
		if(data eq null) {
			try {
				data = Armature.loader.open(name, texRes, shaderRes, fileName)
			} catch {
				case e:IOException => throw NoSuchResourceException(e.getMessage, e)
			}
		} 

		data
	}
}

object ArmatureLibrary { def apply(gl:SGL):ArmatureLibrary = new ArmatureLibrary(gl) }

class ArmatureLibrary(gl:SGL) extends Library[Armature](gl)


// == Behaviors ========================================


object BehaviorResource {
	def apply(name:String, behavior:ArmatureBehavior):BehaviorResource = {
		new BehaviorResource(name, behavior)
	}
}

class BehaviorResource(name:String, val data:ArmatureBehavior) extends ResourceDescriptor[ArmatureBehavior](name) {
	def value(gl:SGL):ArmatureBehavior = data
}

object BehaviorLibrary { def apply(gl:SGL):BehaviorLibrary = new BehaviorLibrary(gl) }

class BehaviorLibrary(gl:SGL) extends Library[ArmatureBehavior](gl)

