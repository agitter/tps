package tps

import GraphSolutions._

import tps.util.LogUtils._

object Graphs {

  case class Vertex(id: String) extends Serializable {
    override def toString = id
  }

  case class Edge(v1: Vertex, v2: Vertex) extends Serializable {
    override def toString = v1.toString + " - " + v2.toString
  }

  case class Path(edges: Seq[Edge]) {
    override def toString: String = {
      edges.head.v1.toString + " - " + edges.map(_.v2).mkString(" - ")
    }

    def start: Vertex = edges.head match {
      case Edge(v, _) => v
    }

    def end: Vertex = edges.last match {
      case Edge(_, v) => v
    }

    def intermediaryVertices: Set[Vertex] = {
      edges.drop(1).map(_.v1).toSet
    }

    def vertices: Set[Vertex] = {
      edges.flatMap{
        case Edge(v1, v2) => Set(v1, v2)
      }.toSet
    }
  }

  object UndirectedGraph {
    /** Creates a graph from given edges. */
    def apply(E: Iterable[Edge]): UndirectedGraph = {
      val V = E flatMap { case Edge(v1, v2) =>
        Set(v1, v2)
      }
      UndirectedGraph(V.toSet, E.toSet, Set.empty)
    }
  }

  case class UndirectedGraph(
    V: Set[Vertex], 
    E: Set[Edge],
    sources: Set[Vertex]
  ) extends Serializable {
  
    // sanity check
    for (Edge(v1, v2) <- E) {
      assert(v1.id <= v2.id)
    }

    override def toString = {
      V.mkString("V = {", ", ", "}") + "\n" +
      E.mkString("E = {", ", ", "}") + "\n" +
      sources.mkString("SRC = {", ", ", "}")
    }

    def neighbors(v: Vertex): Set[Vertex] = {
      E collect {
        case Edge(v1, v2) if v1 == v => v2
        case Edge(v1, v2) if v2 == v => v1
      }
    }

    def incidentEdges(v: Vertex): Set[Edge] = {
      E filter {
        case Edge(v1, v2) => v1 == v || v2 == v
      }
    }

    def contains(e: Edge): Boolean = {
      assert(e.v1.id <= e.v2.id)
      E contains e
    }

    def bfsEdgeOrder: Seq[Edge] = {
      var reachable = sources
      var orderedE = List[Edge]()
      while (orderedE.toSet != E) {
        var newReachable = Set[Vertex]()
        for (e @ Edge(v1, v2) <- E -- orderedE) {
          val v1Reachable = reachable contains v1
          val v2Reachable = reachable contains v2
          if (v1Reachable || v2Reachable) {
            orderedE = orderedE ::: List(e)
            newReachable ++= Set(v1, v2)
          }
        }
        reachable ++= newReachable
      }
      orderedE
    }
  }
}