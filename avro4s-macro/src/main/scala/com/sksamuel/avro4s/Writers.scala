package com.sksamuel.avro4s

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData.Record

import scala.reflect.macros.Context

trait AvroFieldWriter[T] {
  def field(name: String): Schema.Field
}

trait AvroSerializer[T] {
  def write(value: T)(implicit s: AvroSchema[T]): Record
}

trait AvroRecordPut[T] {
  def put(name: String, value: T, record: Record): Unit = record.put(name, value)
}

object Writers {

  implicit val StringWriter: AvroRecordPut[String] = new AvroRecordPut[String] {}
  implicit val BigDecimalSchema: AvroRecordPut[BigDecimal] = new AvroRecordPut[BigDecimal] {}
  implicit val DoubleSchema: AvroRecordPut[Double] = new AvroRecordPut[Double] {}
  implicit val FloatSchema: AvroRecordPut[Float] = new AvroRecordPut[Float] {}
  implicit val BooleanSchema: AvroRecordPut[Boolean] = new AvroRecordPut[Boolean] {}
  implicit val IntSchema: AvroRecordPut[Int] = new AvroRecordPut[Int] {}
  implicit val LongSchema: AvroRecordPut[Long] = new AvroRecordPut[Long] {}

  implicit def ArraySchema[S]: AvroRecordPut[Array[S]] = new AvroRecordPut[Array[S]] {
    override def put(name: String, value: Array[S], record: Record): Unit = {
      record.put(name, value)
    }
  }

  implicit def ListSchema[S]: AvroRecordPut[List[S]] = new AvroRecordPut[List[S]] {
    override def put(name: String, value: List[S], record: Record): Unit = {
      import scala.collection.JavaConverters._
      record.put(name, value.asJavaCollection)
    }
  }

  implicit def SeqSchema[S]: AvroRecordPut[Seq[S]] = new AvroRecordPut[Seq[S]] {
    override def put(name: String, value: Seq[S], record: Record): Unit = {
      import scala.collection.JavaConverters._
      record.put(name, value.asJavaCollection)
    }
  }

  implicit def IterableSchema[S]: AvroRecordPut[Iterable[S]] = new AvroRecordPut[Iterable[S]] {
    override def put(name: String, value: Iterable[S], record: Record): Unit = {
      import scala.collection.JavaConverters._
      record.put(name, value.asJavaCollection)
    }
  }

  def fieldWriter[T](name: String, value: T, record: Record)(implicit s: AvroSchema[T], w: AvroRecordPut[T]): Unit = {
    w.put(name, value, record)
  }

  def impl[T: c.WeakTypeTag](c: Context): c.Expr[AvroSerializer[T]] = {

    import c.universe._
    val t = weakTypeOf[T]

    val fields = t.declarations.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.get.paramss.head

    val fieldWrites: Seq[Tree] = fields.map { f =>
      val termName = f.name.toTermName
      val decoded = f.name.decoded
      val sig = f.typeSignature
      q"""{ import Writers._
            val putter = implicitly[AvroRecordPut[$sig]]
            (t: $t, r: org.apache.avro.generic.GenericData.Record) => {
              putter.put($decoded, t.$termName, r)
            }
          }
      """
    }

    c.Expr[AvroSerializer[T]]( q"""
      new AvroSerializer[$t] {
        import org.apache.avro.generic.GenericData.Record
        import SchemaMacros._
        override def write(t: $t)(implicit s: AvroSchema[$t]): org.apache.avro.generic.GenericData.Record = {
         val r = new org.apache.avro.generic.GenericData.Record(s.schema)
         Seq(..$fieldWrites).foreach(fn => fn(t, r))
         r
        }
      }
    """)
  }
}
