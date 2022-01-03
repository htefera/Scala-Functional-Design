package codecs

/**
  * A data type modeling JSON values.
  *
  * For example, the `42` integer JSON value can be modeled as `Json.Num(42)`
  */
enum Json:
  /** The JSON `null` value */
  case Null
  /** JSON boolean values */
  case Bool(value: Boolean)
  /** JSON numeric values */
  case Num(value: BigDecimal)
  /** JSON string values */
  case Str(value: String)
  /** JSON objects */
  case Obj(fields: Map[String, Json])
  /** JSON arrays */
  case Arr(items: List[Json])

  /**
   * Try to decode this JSON value into a value of type `A` by using
   * the contextual decoder.
   *
   * Note that you have to explicitly fix `A` type parameter when you call the method:
   *
   * {{{
   *   someJsonValue.decodeAs[User] // OK
   *   someJsonValue.decodeAs       // Wrong!
   * }}}
   */
  def decodeAs[A](using decoder: Decoder[A]): Option[A] =
  decoder.decode(this)

/**
  * A type class that turns a value of type `A` into its JSON representation.
  */
trait Encoder[-A]:

  def encode(value: A): Json //turn type A into JSOn

    /**
    * Transforms this `Encoder[A]` into an `Encoder[B]`, given a transformation function
    * from `B` to `A`.
    *
    * For instance, given a `Encoder[String]`, we can get an `Encoder[UUID]`:
    *
    * {{{
    *   def uuidEncoder(given stringEncoder: Encoder[String]): Encoder[UUID] =
    *     stringEncoder.transform[UUID](uuid => uuid.toString)
    * }}}
    *
    * This operation is also known as “contramap”.
    */
  def transform[B](f: B => A): Encoder[B] =
    Encoder.fromFunction[B](value => this.encode(f(value)))

//This object is called a companion object
object Encoder extends EncoderInstances:

  /**
   * Convenient method for creating an instance of encoder from a function `f`
   */
  def fromFunction[A](f: A => Json) = new Encoder[A]:
    def encode(value: A): Json = f(value)

//Implicit encoders instance
trait EncoderInstances:

  /** An encoder for the `Unit` value */
  implicit val unitEncoder:Encoder[Unit] =
    Encoder.fromFunction(_ => Json.Null)

  /** An encoder for `Int` values */
  implicit val intEncoder: Encoder[Int] =
    Encoder.fromFunction(n => Json.Num(BigDecimal(n)))

  /** An encoder for `String` values */
  // TODO Implement the `Encoder[String]` instance
  implicit val stringEncoder: Encoder[String] = Encoder.fromFunction(s => Json.Str(s))


  /** An encoder for `Boolean` values */
  // TODO Define a given value of type `Encoder[Boolean]`
  implicit val booleanEncoder: Encoder[Boolean]=Encoder.fromFunction(b=>Json.Bool(b))
  /**
    * Encodes a list of values of type `A` into a JSON array containing
    * the list elements encoded with the given `encoder`
    */
  //The encoder instance for lists
  implicit def listEncoder[A](using encoder: Encoder[A]): Encoder[List[A]] =
    Encoder.fromFunction(as => Json.Arr(as.map(encoder.encode)))


/**
  * A specialization of `Encoder` that returns JSON objects only
  */
trait ObjectEncoder[-A] extends Encoder[A]:
  // Refines the encoding result to `Json.Obj`
  def encode(value: A): Json.Obj

  /**
    * Combines `this` encoder with `that` encoder.
    * Returns an encoder producing a JSON object containing both
    * fields of `this` encoder and fields of `that` encoder.
    */
  def zip[B](that: ObjectEncoder[B]): ObjectEncoder[(A, B)] =
    ObjectEncoder.fromFunction { case (a, b) =>
      Json.Obj(this.encode(a).fields ++ that.encode(b).fields)
    }

object ObjectEncoder:

  /**
    * Convenient method for creating an instance of object encoder from a function `f`
    */
  def fromFunction[A](f: A => Json.Obj): ObjectEncoder[A] = new ObjectEncoder[A]:
    def encode(value: A): Json.Obj = f(value)

  /**
    * An encoder for values of type `A` that produces a JSON object with one field
    * named according to the supplied `name` and containing the encoded value.
    */
  def field[A](name: String)(using encoder: Encoder[A]): ObjectEncoder[A] =
    ObjectEncoder.fromFunction(a => Json.Obj(Map(name -> encoder.encode(a))))


/**
  * The dual of an encoder. Decodes a serialized value into its initial type `A`.
  */
trait Decoder[+A]:
  /**
    * @param data The data to de-serialize
    * @return The decoded value wrapped in `Some`, or `None` if decoding failed
    */
  def decode(data: Json): Option[A]

  /**
    * Combines `this` decoder with `that` decoder.
    * Returns a decoder that invokes both `this` decoder and `that`
    * decoder and returns a pair of decoded value in case both succeed,
    * or `None` if at least one failed.
    */
  //The zip operation mentioned in the previous section only returns codecs for tuples

  def zip[B](that: Decoder[B]): Decoder[(A, B)] =
    Decoder.fromFunction { json =>
      this.decode(json).zip(that.decode(json))
    }

  /**
    * Transforms this `Decoder[A]` into a `Decoder[B]`, given a transformation function
    * from `A` to `B`.
    *
    * This operation is also known as “map”.
    */
  def transform[B](f: A => B): Decoder[B] =
    Decoder.fromFunction(json => this.decode(json).map(f))

object Decoder extends DecoderInstances:

  /**
    * Convenient method to build a decoder instance from a function `f`
    */
  def fromFunction[A](f: Json => Option[A]): Decoder[A] = new Decoder[A]:
    def decode(data: Json): Option[A] = f(data)

  /**
    * Alternative method for creating decoder instances
    */
  def fromPartialFunction[A](pf: PartialFunction[Json, A]): Decoder[A] =
    fromFunction(pf.lift)


trait DecoderInstances:

  /** A decoder for the `Unit` value */
  implicit val unitDecoder: Decoder[Unit] =
    Decoder.fromPartialFunction { case Json.Null => () }

  /** A decoder for `Int` values. Hint: use the `isValidInt` method of `BigDecimal`. */
  // TODO Define a given value of type `Decoder[Int]`
  // the isValidInt method ensures to reject JSON floating point numbers decoding
  implicit val intDecoder:Decoder[Int]= Decoder.fromPartialFunction{case Json.Num(n) if (n.isValidInt) => n.toInt}


  /** A decoder for `String` values */
  // TODO Define a given value of type `Decoder[String]`
  implicit val stringDecoder: Decoder[String]= Decoder.fromPartialFunction{case Json.Str(s)=>s}


  /** A decoder for `Boolean` values */
  // TODO Define a given value of type `Decoder[Boolean]`
  implicit val booleanDecoder: Decoder[Boolean] = Decoder.fromPartialFunction{case Json.Bool(b)=>b}

  /**
    * A decoder for JSON arrays. It decodes each item of the array
    * using the given `decoder`. The resulting decoder succeeds only
    * if all the JSON array items are successfully decoded.
    */
  // The decoder instance for lists objects
  //need a revision in case grader generate an error
  implicit def listDecoder[A] (using decoder: Decoder[A]): Decoder[List[A]] =
    Decoder.fromFunction { case Json.Arr(items: List[Json]) =>
      val array = items.map(decoder.decode)
      if (array.contains(None)) None  else Option(array.flatten)
           }
//Encoder.fromFunction(as => Json.Arr(as.map(encoder.encode)))
  /**
    * A decoder for JSON objects. It decodes the value of a field of
    * the supplied `name` using the given `decoder`.
    */
  def field[A](name: String)(using decoder: Decoder[A]): Decoder[A] = {
    Decoder.fromFunction { case Json.Obj(fields) => decoder.decode(fields.getOrElse(name, Json.Null))
    } }

case class Person(name: String, age: Int)

object Person extends PersonCodecs

trait PersonCodecs {
  /** The encoder for `Person` */
  implicit val personEncoder: Encoder[Person] = {
    ObjectEncoder.field[String]("name")
      .zip(ObjectEncoder.field[Int]("age"))
      .transform[Person](user => (user.name, user.age))
  }

    /** The corresponding decoder for `Person` */
  implicit val personDecoder: Decoder[Person] =
    Decoder.field[String]("name").zip(Decoder.field[Int]("age")).transform[Person] {
      case (name, age) => Person(name, age)
    }
}

case class Contacts(people: List[Person])

object Contacts extends ContactsCodecs

trait ContactsCodecs {

  // TODO Define the encoder and the decoder for `Contacts`
  // The JSON representation of a value of type `Contacts` should be
  // a JSON object with a single field named “people” containing an
  // array of values of type `Person` (reuse the `Person` codecs)


  implicit val ContactsDecoder: Decoder[Contacts] =
    Decoder.field[List[Person]]("people").transform[Contacts](people => Contacts(people))

  implicit val ContactsEncoder: Encoder[Contacts] =
    ObjectEncoder.field[List[Person]]("people")
      .transform[Contacts](contacts => contacts.people)
  // In case you want to try your code, here is a simple `Main`
  // that can be used as a starting point. Otherwise, you can use
  // the REPL (use the `console` sbt task).
}
object Main {

  import Util.*

  def main(args: Array[String]): Unit =
    println(renderJson(42))
    println(renderJson("foo"))

    val maybeJsonString = parseJson(""" "foo" """)
    val maybeJsonObj = parseJson(""" { "name": "Alice", "age": 42 } """)
    val maybeJsonObj2 = parseJson(""" { "name": "Alice", "age": "42" } """)
    // Uncomment the following lines as you progress in the assignment
     println(maybeJsonString.flatMap(_.decodeAs[Int]))
     println(maybeJsonString.flatMap(_.decodeAs[String]))
     println(maybeJsonObj.flatMap(_.decodeAs[Person]))
     println(maybeJsonObj2.flatMap(_.decodeAs[Person]))
     println(renderJson(Person("Bob", 66)))
}
