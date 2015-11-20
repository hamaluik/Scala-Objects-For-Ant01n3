package org.sofa.math.reconstruction

import scala.math._
import scala.io.BufferedSource
import scala.collection.mutable.ArrayBuffer

import org.sofa.math.{Point3, Vector3, Rgba}

import java.io.{File, InputStream, FileInputStream, FileOutputStream, PrintStream, IOException}


object PointCloud {

	final val PointDesc = """\s*([0-9]+[\.,]?[0-9]*)\s+([0-9]+[\.,]?[0-9]*)\s+(-?[0-9]+[\.,]?[0-9]*)\s*""".r
	final val Empty     = """\s*""".r

	def apply(fileName:String, scaleFactor:Double, yFactor:Double):PointCloud = {
		if(fileName.endsWith(".xyz")) {
			readFileXYZ(fileName, new PointCloud(scaleFactor, yFactor))
		} else {
			throw new RuntimeException("only '.xyz' files are accepted")
		}
	}

	/** Created a [[HeightMap]] from a CSV or ASC file. */
	def readFileXYZ(fileName:String, cloud:PointCloud):PointCloud = {
		val src    = new BufferedSource(new FileInputStream(fileName))
		var curRow = 0
		var i = 0

		src.getLines.foreach { _ match {
			case PointDesc(x, y, z) => { cloud.addPoint(x.toDouble, y.toDouble, z.toDouble) }
			case Empty()            => {}
			case line               => { printf("Unrecognized line '%s'%n", line) }
		}}

		cloud
	}

	var input = ""
	var output:String = null

	def main(args:Array[String]) {
		params(args.toList)

		if(input eq null) {
			throw new RuntimeException("ypu must specify an input...")
		}

		printf("[Reading %s]".format(input))
		val cloud = PointCloud(input, 1.0, 1.0)
		printf("[%d points]%n".format(cloud.points.size))
		printf("[Writing %s]%n".format(if(output ne null) output else "<stdout>"))
		cloud.toObj(output)
	}

	def params(args:List[String]) { args match {
		case Nil => {}
		case "-src" :: file :: tail => { input = file; params(tail) }
		case "-dst" :: file :: tail => { output = file; params(tail) }
		case a :: tail => { throw new RuntimeException("unknwon argument '%s'...".format(a)) }
	}}

}


/** A set of points considered as a cloud and methods to handle such a cloud. */
class PointCloud(scaleFactor:Double, yFactor:Double) {
	
	var points = new ArrayBuffer[Point3]()

	val min = Point3(Double.MaxValue, Double.MaxValue, Double.MaxValue)

	var max = Point3(Double.MinValue, Double.MinValue, Double.MinValue)

	def size:Int = points.size

	def point(i:Int) = points(i)

	def apply(i:Int) = points(i)

	def addPoint(p:Point3) { addPoint(p.x, p.y, p.z) }

	def addPoint(x:Double, y:Double, z:Double) {
		points += Point3(x, y, z)

		if(x < min.x) min.x = x 
		if(x > max.x) max.x = x
		if(y < min.y) min.y = y
		if(y > max.y) max.y = y
		if(z < min.z) min.z = z
		if(z > max.z) max.z = z
	}

	def sortOnX() {
		points = points.sortWith { (a, b) => a.x < b.x }
	}

	def swapYZ() {
		points.foreach { p => p.set(p.x, p.z, p.y) }
	}

	def toOrigin() {
		points.foreach { p => p.set(p.x-min.x, p.y-min.y, p.z-min.z) }
		max.x -= min.x
		max.y -= min.y
		max.z -= min.z
		min.x = 0
		min.y = 0
		min.z = 0
	}

	def normalize(scale:Double) {
		toOrigin()
		val ratio = math.max(max.x, math.max(max.y, max.z))
		points.foreach { p => p.set((p.x/ratio)*scale, scale-((p.y/ratio)*scale), (p.z/ratio)*scale) }
		max.x = scale
		max.y = scale
		max.z = scale
	}

	def toObj(fileName:String) {
		val out = if(fileName eq null) System.out else new PrintStream(new FileOutputStream(fileName))
		points.foreach { p => out.print("v %f %f %f%n".format(p.x-min.x, p.y-min.y, p.z).replace(",", ".")) }
		out.flush
		out.close
	}

	def closePoints(a:Point3, b:Point3, distance:Double=0.001):Boolean = {
		val x = b.x-a.x
		val y = b.y-a.y
		val z = b.z-a.z 
		(x*x + y*y + z*z) < distance*distance
	}

	def closePointsXZ(a:Point3, b:Point3, distance:Double=0.001):Boolean = {
		val x = b.x-a.x
		val z = b.z-a.z 
		(x*x + z*z) < distance*distance
	}

	/** Locate too close points and merge them.
	  * This modifies the points set by removint points that are superposed.
	  * Points are considered one on another if their distance is less than 
	  * the `distance` parameter.
	  * Note that this method alter the points ordering. */
	def mergeClosePoints(close:(Point3,Point3,Double)=>Boolean, distance:Double=0.001) {
		case class IndexedPoint(idx:Int, var tmp:Int)

		sortOnX

		val okPoints = new ArrayBuffer[Point3]()
		val tmpPoints = new ArrayBuffer[IndexedPoint]()
		val n = points.size
		var i = 1
		tmpPoints += IndexedPoint(0, 0)
		val ok = new ArrayBuffer[IndexedPoint]()
		while(i < n) {
			if(i % 1000 == 0) printf("[%d]", i)
			// Browse tmp points to look for doubles and put
			// points that are too far along X in the ok list,
			// since points are sorted along X.
			var j = 0
			var m = tmpPoints.size
			var found = false
			ok.clear()
			while(j < m) {
				if(points(i).x - points(tmpPoints(j).idx).x > distance) ok += tmpPoints(j)
				else if(!found) found = closePoints(points(i), points(tmpPoints(j).idx), distance)
				j += 1
			}
			ok.foreach { p =>
				okPoints += points(p.idx)
				if(p.tmp < m-1) {
					tmpPoints(m-1).tmp = p.tmp
					tmpPoints(p.tmp) = tmpPoints(m-1)
				}
				tmpPoints.remove(m-1)
				m -= 1
			}
			// insert the point in the tmp list if no close match only.
			if(!found) {
				tmpPoints += IndexedPoint(i, tmpPoints.size)
			} else {
				printf("(* %d)", i)
			}

			i += 1
		}

		tmpPoints.foreach { p => okPoints += points(p.idx) }

		printf("%d points -> %d points (%d doubles removed)%n", n, okPoints.size, n-okPoints.size)

		points = okPoints
	}
}
