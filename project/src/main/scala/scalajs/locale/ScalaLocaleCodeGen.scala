package scalajs.locale

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import javax.xml.parsers.SAXParserFactory

import scala.xml.{XML, _}
import scala.collection.JavaConverters._

/**
  * Value objects build out of CLDR XML data
  */
case class XMLLDMLLocale(language: String, territory: Option[String],
                         variant: Option[String], script: Option[String])

case class XMLLDML(locale: XMLLDMLLocale) {
  def scalaSafeName: String = {
    locale.language +
    locale.territory.fold("")(t => s"_$t") +
    locale.variant.fold("")(v => s"_$v") + locale.script.fold("")(s => s"_$s")
  }
}

object CodeGenerator {
  import treehugger.forest._
  import definitions._
  import treehuggerDSL._

  val autoGeneratedCommend = "Auto-generated code from CLDR definitions, don't edit"

  def treeHugIt(ldmls: List[XMLLDML]):Tree = {
    BLOCK (
      IMPORT("scala.scalajs.locale.ldml.LDML") withComment autoGeneratedCommend,
      IMPORT("scala.scalajs.locale.ldml.LDMLLocale"),
      PACKAGEOBJECTDEF("locales") := BLOCK(
        ldmls.map(treeHugIt)
      )
    ) inPackage "scala.scalajs.locale.ldml.data"
  }

  def treeHugIt(ldml: XMLLDML):Tree = {
    val ldmlSym = getModule("LDML")
    val ldmlLocaleSym = getModule("LDMLLocale")

    val ldmlLocaleTree = Apply(ldmlLocaleSym, LIT(ldml.locale.language),
      ldml.locale.territory.fold(NONE)(t => SOME(LIT(t))),
      ldml.locale.variant.fold(NONE)(v => SOME(LIT(v))),
      ldml.locale.script.fold(NONE)(s => SOME(LIT(s))))

    VAL(ldml.scalaSafeName, "LDML") := Apply(ldmlSym, ldmlLocaleTree)
  }

  def metadata(codes: List[String], languages: List[String], scripts: List[String]): Tree = {
    BLOCK (
      OBJECTDEF("metadata") := BLOCK(
        LAZYVAL("isoCountries", "Array[String]") := ARRAY(codes.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("isoLanguages", "Array[String]") := ARRAY(languages.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("scripts", "Array[String]") := ARRAY(scripts.map(LIT(_))) withComment autoGeneratedCommend
      )
    ) inPackage "scala.scalajs.locale.ldml.data"
  }

}

object ScalaLocaleCodeGen {

  def writeGeneratedTree(base: File, file: String, tree: treehugger.forest.Tree):File = {
    val dataPath = base.toPath.resolve("scala").resolve("sacalajs").resolve("ldml").resolve("data")
    val path = dataPath.resolve(s"$file.scala")

    path.getParent.toFile.mkdirs()
    println(s"Write to $path")

    Files.write(path, treehugger.forest.treeToString(tree).getBytes(Charset.forName("UTF8")))
    path.toFile
  }

  def constructLDMLDescriptor(f: File, xml: Elem): (File, XMLLDML) = {
    val language = (xml \ "identity" \ "language" \ "@type").text
    val territory = Option((xml \ "identity" \ "territory" \ "@type").text).filter(_.nonEmpty)
    val variant = Option((xml \ "identity" \ "variant" \ "@type").text).filter(_.nonEmpty)
    val script = Option((xml \ "identity" \ "script" \ "@type").text).filter(_.nonEmpty)
    (f, XMLLDML(XMLLDMLLocale(language, territory, variant, script)))
  }

  val parser: SAXParser = {
    // Use a non validating parser for speed
    val f = SAXParserFactory.newInstance()
    f.setNamespaceAware(false)
    f.setValidating(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.newSAXParser()
  }

  def generateLocaleData(base: File, data: File): Seq[File] = {
    println(data.toPath.resolve("common").resolve("main"))
    val files = Files.newDirectoryStream(data.toPath.resolve("common").resolve("main")).iterator().asScala.toList

    val clazzes = for {
      f <- files.map(k => k.toFile)
    } yield constructLDMLDescriptor(f, XML.withSAXParser(parser).loadFile(f))

    val tree = CodeGenerator.treeHugIt(clazzes.map(_._2))

    val isoCountryCodes = clazzes.flatMap(_._2.locale.territory).distinct.filter(_.length == 2).sorted
    val isoLanguages = clazzes.map(_._2.locale.language).distinct.filter(_.length == 2).sorted
    val scripts = clazzes.flatMap(_._2.locale.script).distinct.sorted

    val f1 = writeGeneratedTree(base, "locales", tree)
    val f2 = writeGeneratedTree(base, "metadata", CodeGenerator.metadata(isoCountryCodes, isoLanguages, scripts))
    Seq(f1, f2)
  }
}
