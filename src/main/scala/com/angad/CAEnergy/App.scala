package com.angad.CAEnergy

import com.twitter.finatra._
import org.apache.commons.csv._
import java.io._
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

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

  class CountyView(e: String, data: List[String]) extends View {
    val template = "county_view.mustache"
    val records = data
    val energy = e
  }

  class CountyDetailView(c: String, data: List[HashMap[String, String]]) extends View {
    val template = "county_detail_view.mustache"
    val records = data
    val county = c
  }

  class PlanningareaView(e: String, data: List[String]) extends View {
    val template = "planningarea_view.mustache"
    val records = data
    val energy = e
  }

  class PlanningareaDetailView(c: String, data: List[Map[String, String]]) extends View {
    val template = "planningarea_detail_view.mustache"
    val records = data
    val planningarea = c
  }

  def cleanString(s: String) = s.trim().replaceAll("[^\\x00-\\x7F]", "").replaceAll("\"", "")

  def isAllDigits(x: String) = x forall Character.isDigit

  def sanitizeByCounty(data: List[Map[String, String]], county: String) = {
    val county_data = data.filter((p: Map[String, String]) => p("County").toLowerCase == county.toLowerCase)
    val headers = data(0).keys.toList
    var l = List[HashMap[String, String]]()
    for (i <- 0 to headers.length-1)
      if (isAllDigits(headers(i))) {
        val h = HashMap[String, String]()
        h("Year") = headers(i)
        h(county_data(0)("Sector")) = county_data(0)(headers(i))
        h(county_data(1)("Sector")) = county_data(1)(headers(i))
        h(county_data(2)("Sector")) = county_data(2)(headers(i))
        l = h :: l
      }
    l
  }

  def sanitizeByPlanningarea(data: List[Map[String, String]], planningarea: String) = {
    data.filter((p: Map[String, String]) => p("Planning Area Description").toLowerCase == planningarea.toLowerCase)
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
        case "county" => render.view(new CountyView(energy, l.map { case (x: Map[String, String]) => x("County").toLowerCase}.distinct)).toFuture
        case "planningarea" => render.view(new PlanningareaView(energy, l.map { case (x: Map[String, String]) => x("Planning Area Description").toLowerCase}.distinct)).toFuture
        case _ => throw new NotFound
      }
    }

    get("/data/:energy/county/:county") { request =>
      val energy = request.routeParams.getOrElse("energy", "electricity")
      val county = request.routeParams.getOrElse("county", "alameda")
      val l = getData(energy, "county")
      val county_data = sanitizeByCounty(l, county)
      render.view(new CountyDetailView(county, county_data)).toFuture
    }

    get("/data/:energy/planningarea/:planningarea") { request =>
      val energy = request.routeParams.getOrElse("energy", "electricity")
      val planningarea = request.routeParams.getOrElse("planningarea", "alameda")
      val l = getData(energy, "planningarea")
      val planningarea_data = sanitizeByPlanningarea(l, planningarea)
      render.view(new PlanningareaDetailView(planningarea, planningarea_data)).toFuture
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
