package org.sofa.math

import org.sofa.nio._
import scala.math._

/** Simple sequence of numbers.
  * 
  * This is the basis for vectors or any size, points or any kind of set of
  * numbers.
  * 
  * ==A note on the design of the math library==
  * 
  * The choice in designing the math library was to always use double real numbers instead of
  * creating a version of each class for floats or doubles, or using type parameters. This
  * design choice  implies that sometimes one will have to copy an array in a given format to
  * another. */
trait NumberSeq extends IndexedSeq[Double] {
    
// Attribute
    
    /** The return type of operations that generate a new NumberSeq.
      *
      * As +, -, * and / must return a NumberSeq as this trait is
      * specialized as a Vector or Point, such operations should instead
      * return a Vector or a Point not a NumberSeq. This type is therefore
      * specialized in the concrete classes that use it. */
    type ReturnType <: NumberSeq
    
    /** Real content. */
    protected[math] val data:Array[Double]
 
// Access
    
    /** Number of elements. This is defined in SeqLike, do not confuse with norm !!. */
    def length:Int = data.length

    /** `i`-th element. */
    def apply(i:Int):Double = data(i)
	
    /** True if all components are zero. */
	def isZero:Boolean = {
        var ok = true
        var i  = 0
        val n  = size
        while(i < n) {
            if(data(i) != 0) {
                i = n
                ok = false
            }
        }
        ok
    }
	
	/** True if all components are zero. */
	def isOrigin:Boolean = isZero

	override def toString():String = {
	    val buf = new StringBuffer
	    
	    buf.append("(")
	    buf.append(mkString(", "))
	    buf.append(")")
	    buf.toString
	}
	
	/** New number sequence of the same size as this. This is not a copy of the element of this. */
	protected[math] def newInstance():ReturnType

// Conversion

    /** This sequence as an array of doubles. There is no convertion, since this is the native format. */
    def toDoubleArray:Array[Double] = data
    
    /** This sequence converted as an array of floats.
      *
      * If the sequence is not backed by a float array, a conversion occurs.
      */
    def toFloatArray:Array[Float] = {
        val n     = data.length
        var i     = 0
        val array = new Array[Float](n)
        while(i < n) {
            array(i) = data(i).toFloat
            i += 1
        }
        array
    }
    
    /** This sequence converted as a NIO buffer of doubles.
      *
      * If the sequence is not backed by a NIO buffer of doubles, a conversion occurs.
      */
    def toDoubleBuffer:DoubleBuffer = {
        val n   = data.length
        var i   = 0
        val buf = new DoubleBuffer(n)
        while(i < n) {
            buf(i) = data(i).toDouble
            i += 1
        }
        buf.rewind
        buf
    }
    
    /** This sequence converted  as a NIO buffer of floats.
      *
      * If the sequence is not backed by a NIO buffer of floats, a conversion occurs.
      */
    def toFloatBuffer:FloatBuffer = {
        val n   = data.length
        var i   = 0
        val buf = new FloatBuffer(n)
        while(i < n) {
            buf(i) = data(i).toFloat
            i += 1
        }
        buf.rewind
        buf
    }
    
// Modification

    /** Is the size of `other` the same as this ? If not throw a `RuntimeException`. */
    protected def checkSizes(other:NumberSeq) {
    	if(other.size != size) throw new RuntimeException("operation available on number sequences of same size only")
    }
    
    /** Assign `value` to the `i`-th element. */
    def update(i:Int, value:Double) = data(i) = value
    
    /** Copy the content of `data` in this.
      * 
      * The size of the smallest sequence determine the number of elements copied. */
    def copy(data:Traversable[Double]) {
        val n = math.min(size, data.size) 
        var i = 0
        
        data.foreach { item =>
            if(i < n) {
            	this.data(i) = item
            }
            i += 1
        }
    }

    /** Copy the content of `other` in this.
      *
      * The size of the smallest sequence determine the number of elements copied. */
    def copy(other:NumberSeq) {
    	// Much faster than the general copy(Traversable), no foreach.
    	val n = math.min(size, other.size)
    	var i = 0
    	var o = other.data

    	while(i < n) {
    		this.data(i) = o(i)
    		i += 1
    	}
    }

	/** Copy `value` in each component. */
	def fill(value:Double) {
	    val n = size
	    var i = 0
	    while(i < n) {
	    	data(i) = value
	    	i += 1
	    }
	}

	/** Add each element of `other` to the corresponding element of this.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be added, starting at 0.
	  */
	def addBy(other:NumberSeq):ReturnType = {
	    val n = math.min(size, other.size)
	    var i = 0
	    while(i < n) {
	    	data(i) += other(i)
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}

	/** Add `value` to each element of this.
	  *
	  * This modifies in place this sequence.
	  */
	def addBy(value:Double):ReturnType = {
	    val n = size
	    var i = 0
	    while(i < n) {
	    	data(i) += value
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}

	/** Add each element of `other` to the corresponding element of this.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be added, starting at 0.
	  */
	def +=(other:NumberSeq):ReturnType = addBy(other)

	/** Add `value` to each element of this.
	  *
	  * This modifies in place this sequence.
	  */
	def +=(value:Double):ReturnType = addBy(value)
	
	/** Result of the addition of each element of this by the corresponding element of
	  * `other`.
	  * 
	  * The two sequences must have the same size.
	  * 
	  * @return a new number sequence result of the addition.
	  */
    def +(other:NumberSeq):ReturnType = {
        checkSizes(other)
        val result = newInstance
        result.copy(this)
        result.addBy(other)
        result
    }
    
    /** Result of the addition of value to each element of this.
      * 
      * @return a new number sequence result of the addition. 
      */
    def +(value:Double):ReturnType = {
        val result = newInstance
        result.copy(this)
        result.addBy(value)
        result
    }

	/** Subtract each element of `other` to the corresponding element of this.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be added, starting at 0.
	  */
	def subBy(other:NumberSeq):ReturnType = {
	    val n = math.min(size, other.size)
	    var i = 0
	    while(i < n) {
	    	data(i) -= other(i)
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}

	/** Subtract `value` to each element of this.
	  *
	  * This modifies in place this sequence.
	  */
	def subBy(value:Double):ReturnType = {
	    val n = size
	    var i = 0
	    while(i < n) {
	    	data(i) -= value
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}

	/** Subtract each element of `other` to the corresponding element of this.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be added, starting at 0.
	  */
	def -=(other:NumberSeq):ReturnType = subBy(other)

	/** Subtract `value` to each element of this.
	  *
	  * This modifies in place this sequence.
	  */
	def -=(value:Double):ReturnType = subBy(value)
	
	/** Result of the subtraction of each element `other` to the corresponding element of
	  * this.
	  * 
	  * The two sequences must have the same size.
	  * 
	  * @return a new number sequence result of the subtraction.
	  */
    def -(other:NumberSeq):ReturnType = {
        checkSizes(other)
        val result = newInstance
        result.copy(this)
        result.subBy(other)
        result
    }
    
    /** Result of the subtraction of value to each element of this.
      * 
      * @return a new number sequence result of the subtraction. 
      */
    def -(value:Double):ReturnType = {
        val result = newInstance
        result.copy(this)
        result.subBy(value)
        result
    }
	
	/** Multiply each element of `other` with the corresponding element of this.
	  * 
	  * The two sequences must have the same size.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be multiplied, starting at 0.
	  */
	def multBy(other:NumberSeq):ReturnType = {
	    val n = math.min(size, other.size)
	    var i = 0
	    while(i < n) {
	    	data(i) *= other(i)
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}

	/** Multiply each element of this by `value`.
	  * 
	  * This modifies in place this sequence.
	  */
	def multBy(value:Double):ReturnType = {
	    val n = size
	    var i = 0
	    while(i < n) {
	    	data(i) *= value
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}

	/** Multiply each element of `other` with the corresponding element of this.
	  * 
	  * The two sequences must have the same size.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be multiplied, starting at 0.
	  */
	def *=(other:NumberSeq):ReturnType = multBy(other)

	/** Multiply each element of this by `value`.
	  * 
	  * This modifies in place this sequence.
	  */
	def *=(value:Double):ReturnType = multBy(value)
	
	/** Result of the multiplication of each element of this by the corresponding element of
	  * `other`.
	  * 
	  * The two sequences must have the same size.
	  * 
	  * @return a new number sequence result of the multiplication.
	  */
	def *(other:NumberSeq):ReturnType = {
	    checkSizes(other)
	    val result = newInstance
	    result.copy(this)
	    result.multBy(other)
	    result
	}
	
	/** Result of the multiplication of each element of this by `value`.
	  * 
	  * @return a new number sequence result of the multiplication.
	  */
	def *(value:Double):ReturnType = {
	    val result = newInstance
	    result.copy(this)
	    result.multBy(value)
	    result
	}
	
	/** Divide each element of this by the corresponding element of `other`.
	  * 
	  * The two sequences must have the same size.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be divided, starting at 0.
	  */
	def divBy(other:NumberSeq):ReturnType = {
	    checkSizes(other)
	    val n = math.min(size, other.size)
	    var i = 0
	    while(i < n) {
	    	data(i) /= other(i)
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}

	/** Divide each element of this by `value`.
	  * 
	  * This modifies in place this sequence.
	  */
	def divBy(value:Double):ReturnType = {
	    val n = size
	    var i = 0
	    while(i < n) {
	    	data(i) /= value
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}
	
	/** Divide each element of this by the corresponding element of `other`.
	  * 
	  * The two sequences must have the same size.
	  *
	  * This modifies in place this sequence. The size of the smallest sequence determines the
	  * number of elements to be divided, starting at 0.
	  */
	def /=(other:NumberSeq):ReturnType = divBy(other)

	/** Divide each element of this by `value`.
	  * 
	  * This modifies in place this sequence.
	  */
	def /=(value:Double):ReturnType = divBy(value)
	
	/** Result of the division of each element of this by the corresponding element of
	  * `other`.
	  * 
	  * The two sequences must have the same size.
	  * 
	  * @return a new number sequence result of the division.
	  */
	def /(other:NumberSeq):ReturnType = {
	    checkSizes(other)
	    val result = newInstance
	    result.copy(this)
	    result.divBy(other)
	    result
	}

	/** Result of the division of each element of this by `value`.
	  * 
	  * @return a new number sequence result of the division.
	  */
	def /(value:Double):ReturnType = {
	    val result = newInstance
	    result.copy(this)
	    result.divBy(value)
	    result
	}
	
	/** Dot product of this by the set of `values`.
	  * 
	  * The set of `values` must have at least the same number of components as this sequence,
	  * else the dot product is made on the minimum number of elements. 
	  */
	def dot(values:Double*):Double = {
	    val n = math.min(values.length, size)
	    var i = 0
	    var result = 0.0
	    while(i < n) {
	        result += data(i) * values(i)
	        i += 1
	    }
	    result
	}
	
	/** Dot product of this by `other`.
	  * 
	  * The two sequences must have the same size.
	  */
	def dot(other:NumberSeq):Double = {
	    checkSizes(other)
	    val n = size
	    var i = 0
		var result = 0.0
		while(i < n) {
		    result += data(i) * other.data(i)
		    i += 1
		}
		result
	}
	
	/** Dot product of `this` and `other`.
	  * 
	  * The two sequences must have the same size.
	  */
	def **(other:NumberSeq):Double = dot(other)
	
	/** Magnitude of this (length in terms of distance). */
	def norm:Double = {
		var result = 0.0
		var i = 0
		val n = size
		while(i < n) {
			result += data(i) * data(i)
			i += 1
		}
		math.sqrt(result)

	    // var result = 0.0
	    // foreach { item => result += item * item }
	    // scala.math.sqrt(result)
	}
	
	/** Multiply each element of this by the norm of this.
	  * 
	  * Changes are applied to this in place. 
	  */
	def normalize():Double = {
	    val len = norm
	    var i   = 0
	    val n   = size
	    while(i < n) {
	        data(i) /= len
	        i += 1
	    }
	    len
	}
	
	/** Result of the normalization of this. 
	  * 
	  * @return a new number sequence normalization of this.
	  * @see [[normalize]]
	  */
	def normalized():ReturnType = {
	    val result = newInstance
	    result.copy(this)
	    result.normalize
	    result
	}
	
	/** Arbitrarily move this point in a random direction of a maximum given factor. */
	def brownianMotion(factor:Double) {
		var i = 0
		val n = size
		while(i < n) {
			data(i) += (math.random-0.5) * factor
			i += 1
		}
	}

	/** Store in this number seq the maximum value component-wise with `other`. */
	def maxBy(other:NumberSeq):ReturnType = {
		checkSizes(other)
	    val n = math.min(size, other.size)
	    var i = 0
	    while(i < n) {
	    	if(data(i) < other(i))
	    		data(i) = other(i)
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}
	
	/** Store in this number seq the minimum value component-wise with `other`. */
	def minBy(other:NumberSeq):ReturnType = {
		checkSizes(other)
	    val n = math.min(size, other.size)
	    var i = 0
	    while(i<n) {
	    	if(data(i) > other(i))
	    		data(i) = other(i)
	    	i += 1
	    }
	    this.asInstanceOf[ReturnType]
	}
}

//===================================================

trait NumberSeq2 extends NumberSeq {
    final def x:Double = data(0)
    final def y:Double = data(1)
    final def xy:(Double, Double) = (data(0), data(1))
    
    final def x_=(value:Double) = data(0) = value
    final def y_=(value:Double) = data(1) = value
    final def xy_=(value:(Double, Double)) = { data(0) = value._1; data(1) = value._2 }
    
    def set(x:Double, y:Double):ReturnType = { data(0) = x; data(1) = y; this.asInstanceOf[ReturnType] }
    
    def copy(other:NumberSeq2) {
        // Much faster than original on n elements.
        val o = other.data
        data(0) = o(0)
        data(1) = o(1)
    }

    override def norm:Double = {
        // Much faster than original on n elements.
        math.sqrt(data(0)*data(0) + data(1)*data(1))
    }
    
    override def normalize():Double = {
        // Much faster than original on n elements.
        val len = norm
        data(0) /= len
        data(1) /= len
        len
    }

    def +=(other:NumberSeq2):ReturnType = addBy(other)

    override def +=(value:Double):ReturnType = addBy(value)

    def addBy(other:NumberSeq2):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) += o(0)
        data(1) += o(1)
        this.asInstanceOf[ReturnType]
    }

    override def addBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) += value
        data(1) += value
        this.asInstanceOf[ReturnType]
    }

    def -=(other:NumberSeq2):ReturnType = subBy(other)

    override def -=(value:Double):ReturnType = subBy(value)

    def subBy(other:NumberSeq2):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) -= o(0)
        data(1) -= o(1)
        this.asInstanceOf[ReturnType]
    }

    override def subBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) -= value
        data(1) -= value
        this.asInstanceOf[ReturnType]
    }

    def *=(other:NumberSeq2):ReturnType = multBy(other)

    override def *=(value:Double):ReturnType = multBy(value)

    def multBy(other:NumberSeq2):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) *= o(0)
        data(1) *= o(1)
        this.asInstanceOf[ReturnType]
    }

    override def multBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) *= value
        data(1) *= value
        this.asInstanceOf[ReturnType]
    }

    def /=(other:NumberSeq2):ReturnType = divBy(other)

    override def /=(value:Double):ReturnType = divBy(value)

    def divBy(other:NumberSeq2):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) /= o(0)
        data(1) /= o(1)
        this.asInstanceOf[ReturnType]
    }

    override def divBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) /= value
        data(1) /= value
        this.asInstanceOf[ReturnType]
    }
}

//===================================================

trait NumberSeq3 extends NumberSeq2 {
	final def z:Double = data(2)
    final def yz:(Double, Double) = (data(1), data(2))
    final def xz:(Double, Double) = (data(0), data(2))
    final def xyz:(Double, Double, Double) = (data(0), data(1), data(2))
    
    final def z_=(value:Double) = data(2) = value
    final def yz_=(value:(Double, Double)) = { data(1) = value._1; data(2) = value._2 }
    final def xz_=(value:(Double, Double)) = { data(0) = value._1; data(2) = value._2 }
    final def xyz_=(value:(Double, Double, Double)) = { data(0) = value._1; data(1) = value._2; data(2) = value._3 }
    
    def set(x:Double, y:Double, z:Double):ReturnType = { data(0) = x; data(1) = y; data(2) = z; this.asInstanceOf[ReturnType] }
        
    def copy(other:NumberSeq3) {
        // Much faster than original on n elements.
        val o = other.data
        data(0) = o(0)
        data(1) = o(1)
        data(2) = o(2)
    }

    override def norm:Double = {
        // Much faster than original on n elements.
        math.sqrt(data(0)*data(0) + data(1)*data(1) + data(2)*data(2))
    }
    
    override def normalize():Double = {
        // Much faster than original on n elements.
        val len = norm
        data(0) /= len
        data(1) /= len
        data(2) /= len
        len
    }

    def +=(other:NumberSeq3):ReturnType = addBy(other)

    override def +=(value:Double):ReturnType = addBy(value)

    def addBy(other:NumberSeq3):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) += o(0)
        data(1) += o(1)
        data(2) += o(2)
        this.asInstanceOf[ReturnType]
    }

    override def addBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) += value
        data(1) += value
        data(2) += value
        this.asInstanceOf[ReturnType]
    }

    def -=(other:NumberSeq3):ReturnType = subBy(other)

    override def -=(value:Double):ReturnType = subBy(value)

    def subBy(other:NumberSeq3):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) -= o(0)
        data(1) -= o(1)
        data(2) -= o(2)
        this.asInstanceOf[ReturnType]
    }

    override def subBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) -= value
        data(1) -= value
        data(2) -= value
        this.asInstanceOf[ReturnType]
    }

    def *=(other:NumberSeq3):ReturnType = multBy(other)

    override def *=(value:Double):ReturnType = multBy(value)

    def multBy(other:NumberSeq3):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) *= o(0)
        data(1) *= o(1)
        data(2) *= o(2)
        this.asInstanceOf[ReturnType]
    }

    override def multBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) *= value
        data(1) *= value
        data(2) *= value
        this.asInstanceOf[ReturnType]
    }

    def /=(other:NumberSeq3):ReturnType = divBy(other)

    override def /=(value:Double):ReturnType = divBy(value)

    def divBy(other:NumberSeq3):ReturnType = {
        // Much faster than original on n elements.
        val o = other.data
        data(0) /= o(0)
        data(1) /= o(1)
        data(2) /= o(2)
        this.asInstanceOf[ReturnType]
    }

    override def divBy(value:Double):ReturnType = {
        // Much faster than original on n elements.
        data(0) /= value
        data(1) /= value
        data(2) /= value
        this.asInstanceOf[ReturnType]
    }
}

//===================================================

trait NumberSeq4 extends NumberSeq3 {
	final def w:Double = data(3)
    final def xw:(Double, Double) = (data(0), data(3))
    final def yw:(Double, Double) = (data(1), data(3))
    final def zw:(Double, Double) = (data(2), data(3))
    final def xyw:(Double, Double, Double) = (data(0), data(1), data(3))
    final def xzw:(Double, Double, Double) = (data(0), data(2), data(3))
    final def yzw:(Double, Double, Double) = (data(1), data(2), data(3))
    final def xyzw:(Double, Double, Double, Double) = (data(0), data(1), data(2), data(3))
    
    final def w_=(value:Double) = data(3) = value
    final def xw_=(value:(Double, Double)) = { data(0) = value._1; data(3) = value._2 }
    final def yw_=(value:(Double, Double)) = { data(1) = value._1; data(3) = value._2 }
    final def zw_=(value:(Double, Double)) = { data(2) = value._1; data(3) = value._2 }
    final def xyw_=(value:(Double, Double, Double)) = { data(0) = value._1; data(1) = value._2; data(3) = value._3 }
    final def xzw_=(value:(Double, Double, Double)) = { data(0) = value._1; data(2) = value._2; data(3) = value._3 }
    final def yzw_=(value:(Double, Double, Double)) = { data(1) = value._1; data(2) = value._2; data(3) = value._3 }
    final def xyzw_=(value:(Double, Double, Double, Double)) = { data(0) = value._1; data(1) = value._2; data(2) = value._3; data(3) = value._4 }

    def set(x:Double, y:Double, z:Double, w:Double):ReturnType = { data(0) = x; data(1) = y; data(2) = z; data(3) = w; this.asInstanceOf[ReturnType] }
}
