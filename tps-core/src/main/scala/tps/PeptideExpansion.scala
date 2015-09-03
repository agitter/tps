package tps

import Graphs._
import GraphOps._
import GraphSolutions._

import util.{MathUtils,StringUtils}

object PeptideExpansion {
  type PeptideProteinMap = Map[String, Set[String]]

  /** Augments time-series data with protein prefix. */
  def expandTimeSeries(
    ts: TimeSeries,
    ppm: PeptideProteinMap
  ): TimeSeries = {
    val newProfiles = ts.profiles flatMap { p =>
      val pepID = p.id
      ppm.get(pepID) match {
        case Some(protIDs) => 
          protIDs.toList.map{ case protID =>
            Profile(
              StringUtils.uniquePeptideID(pepID, protID),
              p.values
            )
          }
        case None => List(p)
      }
    }
    TimeSeries(ts.labels, newProfiles)
  }

  def expandScores(
    scores: Map[String, Seq[Double]],
    ppm: PeptideProteinMap
  ): Map[String, Seq[Double]] = {
    expandMap(scores, ppm)
  }

  def expandGraph(g: UndirectedGraph, ppm: PeptideProteinMap): UndirectedGraph = {
    val proteinsToPeptides = proteinsToExpandedPeptides(ppm)
    val protCliques: Map[Vertex, UndirectedGraph] = (for (v <- g.V) yield {
      val expandedV = proteinToPeptideVertices(v, proteinsToPeptides)
      val addCliqueEdges = false
      val expanded = if (addCliqueEdges) clique(expandedV) else emptyGraph.copy(V = expandedV)
      (v, expanded)
    }).toMap

    val interCliqueEdges = g.E flatMap {
      case Edge(v1, v2) => {
        val clique1 = protCliques(v1)
        val clique2 = protCliques(v2)
        val expandedE = clique1.V flatMap {
          innerV1 => clique2.V map {
            innerV2 => 
              if (innerV1.id < innerV2.id) Edge(innerV1, innerV2)
              else Edge(innerV2, innerV1)
          }
        }
        expandedE
      }
    }

    val expandedSrc = g.sources flatMap {
      v => protCliques(v).V
    }

    val cliqueUnion = protCliques.values.foldLeft(emptyGraph)(union(_, _))
    UndirectedGraph(
      cliqueUnion.V, 
      cliqueUnion.E ++ interCliqueEdges, 
      expandedSrc
    )
  }

  def collapseSolution(
    sol: AmbiguousGraphSolution, 
    ppm: PeptideProteinMap
  ): AmbiguousGraphSolution = {
    val peptidesToProteins = expandedPeptidesToProteins(ppm)
    var collapsedSol: AmbiguousGraphSolution = Map.empty

    for ((e @ Edge(v1, v2), ess) <- sol) {
      val origSrc = peptidesToProteins.get(v1.id).getOrElse(v1.id)
      val origDst = peptidesToProteins.get(v2.id).getOrElse(v2.id)
      // do not create self-edges
      if (origSrc != origDst) {
        assert(origSrc < origDst)
        val collapsedEdge = Edge(Vertex(origSrc), Vertex(origDst))
        val newEdgeSol = collapsedSol.get(collapsedEdge).getOrElse(Set()) ++ ess
        collapsedSol += collapsedEdge -> newEdgeSol
      }
    }

    collapsedSol
  }

  def expandSolution(
    s: AmbiguousGraphSolution, 
    ppm: PeptideProteinMap
  ): AmbiguousGraphSolution = {
    val proteinsToPeptides = proteinsToExpandedPeptides(ppm)
  
    val expandedSol = s flatMap { case (Edge(v1, v2), ess) =>
      val peptideIds1 = proteinsToPeptides.getOrElse(v1.id, Set(v1.id))
      val peptideIds2 = proteinsToPeptides.getOrElse(v2.id, Set(v2.id))
      MathUtils.cartesianProduct(peptideIds1, peptideIds2) map { case (id1, id2) =>
        assert(id1 < id2)
        Edge(Vertex(id1), Vertex(id2)) -> ess
      }
    }

    expandedSol
  }

  // this one maps prot#pept IDs to prot IDs
  def expandedPeptidesToProteins(
    ppm: PeptideProteinMap
  ): Map[String, String] = {
    ppm flatMap { case (pepID, protIDs) =>
      protIDs map { protID =>
        StringUtils.uniquePeptideID(pepID, protID) -> protID
      }
    }
  }

  // this one maps prot IDs to created prot#pept IDs
  def proteinsToExpandedPeptides(
    ppm: PeptideProteinMap
  ): Map[String, Set[String]] = {
    var expansionSets = Map[String, Set[String]]()
    for ((pepID, protIDs) <- ppm) {
      for (protID <- protIDs) {
        val newPepID = StringUtils.uniquePeptideID(pepID, protID)
        val newSet = expansionSets.get(protID) match {
          case Some(peps) => peps + newPepID
          case None => Set(newPepID)
        }
        expansionSets += protID -> newSet
      }
    }
    expansionSets
  }

  /* for every matching pep-prot, return value mapped by f */
  private def mapPairs[A,B,C](mapping: Map[A, Set[B]], f: (A, B) => Option[C]): Iterable[C] = {
    mapping flatMap { case (k, vs) =>
      vs.map(v => f(k, v)).filter(_.isDefined).map(_.get)
    }
  }

  private def expandMap[A](toExpand: Map[String, A], expansionMap: Map[String, Set[String]]): Map[String, A] = {
    def f(pepID: String, protID: String): Option[(String, A)] = {
      toExpand.get(pepID) map { mapped =>
        val key = StringUtils.uniquePeptideID(pepID, protID)
        key -> toExpand(pepID)
      }
    }
    mapPairs(expansionMap, f).toMap
  }
}