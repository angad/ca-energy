package com.angad.CAEnergy

import com.twitter.finatra._
import org.apache.commons.csv._
import java.io._
import scala.collection.JavaConverters._

object App extends FinatraServer {

  class NotFound extends Exception

  class DataView(ts: Long) extends View {
    val template = "index_view.mustache"
    val timestamp = ts
  }

  class UtilityView(data: List[Map[String, String]]) extends View {
    val template = "utility_view.mustache"
    val records = data
  }

  class LinechartView(e: String, b: String, data: List[String]) extends View {
    val template = "linechart_view.mustache"
    val records = data
    val energy = e
    val by = b
  }

  def cleanString(s: String) = s.trim().replaceAll("[^\\x00-\\x7F]", "").replaceAll("\"", "")

  def isAllDigits(x: String) = x forall Character.isDigit

  def sanitizeByCounty(data: List[Map[String, String]], county: String) = {
    val county_data = data.filter((p: Map[String, String]) => p("County").toLowerCase == county.toLowerCase)
    val headers = data(0).keys.toList.sorted(Ordering[String].reverse)
    var l = List[List[String]]()
    for (header <- headers) {
      if (isAllDigits(header)) {
        l = List[String](header,
          county_data.filter(x => x("Sector") == "Non-Residential")(0)(header),
          county_data.filter(x => x("Sector") == "Residential")(0)(header),
          county_data.filter(x => x("Sector") == "Total")(0)(header)
        ) :: l
      }
    }
    l = List[String]("Year", "Non-Residential", "Residential", "Total") :: l
    l
  }

  def sanitizeByPlanningarea(data: List[Map[String, String]], planningarea: String) = {
    val planningarea_data = data.filter((p: Map[String, String]) => p("Planning Area Description").toLowerCase == planningarea.toLowerCase)
    val keys = List[String]("Year", "Residential", "Industry", "Ag & Water Pump",
      "Total Usage", "Commercial Other", "Mining & Construction", "Commercial Building")
    var l = List[List[String]]()
    for (p <- planningarea_data) {
      var k = List[String]()
      for (key <- keys) {
        k = p(key) :: k
      }
      l = k.reverse :: l
    }
    keys :: l
  }

  def getData(energy: String, by: String): List[Map[String, String]] = {
    val filename = "data/" + energy + "by" + by + ".csv"
    var in: Reader = null
    try {
      in = new FileReader(filename)
    } catch {
      case ex: IOException => throw new NotFound
    }

    val records = CSVFormat.EXCEL.parse(in).getRecords().asScala
    val headers = records.head.asScala.toList.map {
      case(s: String) => cleanString(s)
    }
    val rows = records.tail.toList.map {
      case(row: CSVRecord) => row.asScala.toList.map {
        case(s: String) => cleanString(s)
      }
    }
    var l = List[Map[String, String]]()
    for (r <- rows) l = (headers zip r).toMap :: l
    l
  }

  class EnergyApp extends Controller {

    get("/") { request =>
      render.view(new DataView(System.currentTimeMillis / 1000)).toFuture
    }

    get("/data/:energy/:by") { request =>
      val energy = request.routeParams.getOrElse("energy", "electricity")
      val by = request.routeParams.getOrElse("by", "county")
      val l = getData(energy, by)
      by match {
        case "utility" => render.view(new UtilityView(l)).toFuture
        case "county" => render.view(
          new LinechartView(energy, by, l.map {
            case (x: Map[String, String]) =>
              x("County").toLowerCase}.distinct.sorted(Ordering[String].reverse)
          )
        ).toFuture
        case "planningarea" => render.view(
          new LinechartView(energy, by,  l.map {
            case (x: Map[String, String]) =>
              x("Planning Area Description").toLowerCase}.distinct
          )
        ).toFuture
        case _ => throw new NotFound
      }
    }

    get("/data/:energy/county/:county") { request =>
      val energy = request.routeParams.getOrElse("energy", "electricity")
      val county = request.routeParams.getOrElse("county", "alameda")
      val l = getData(energy, "county")
      val county_data = sanitizeByCounty(l, county)
      render.json(county_data).toFuture
    }

    get("/data/:energy/planningarea/:planningarea") { request =>
      val energy = request.routeParams.getOrElse("energy", "electricity")
      val planningarea = request.routeParams.getOrElse("planningarea", "alameda")
      val l = getData(energy, "planningarea")
      val planningarea_data = sanitizeByPlanningarea(l, planningarea)
      render.json(planningarea_data).toFuture
    }

    error { request =>
      request.error match {
        case Some(e:NotFound) =>
          render.status(404).json(Map("status" -> "not found")).toFuture
        case _ =>
          render.status(500).plain("Something went wrong!").toFuture
      }
    }

    notFound { request =>
      render.status(404).plain("not found yo").toFuture
    }
  }

  register(new EnergyApp())
}
