package org.silkframework.workspace.activity.workflow

import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, MustMatchers}
import org.silkframework.dataset.Dataset
import org.silkframework.util.Identifier
import org.silkframework.workspace.{Project, ProjectTask}

/**
  * Created on 7/21/16.
  */
class WorkflowTest extends FlatSpec with MockitoSugar with MustMatchers {
  behavior of "Workflow"

  val DS_A = "dsA"
  val DS_A1 = "dsA1"
  val DS_A2 = "dsA2"
  val DS_B = "dsB"
  val DS_B1 = "dsB1"
  val DS_B2 = "dsB2"
  val TRANSFORM = "transform"
  val TRANSFORM_1 = "transform1"
  val TRANSFORM_2 = "transform2"
  val LINKS = "links"
  val OUTPUT = "output"
  val LINKING = "linking"
  val GENERATE_OUTPUT = "generateOutput"
  val WORKFLOW = "workflow"
  val OP_1 = "op1"
  val OP_2 = "op2"
  val DS_C = "dsC"

  it should "support sorting its workflow operators topologically" in {
    val project = mock[Project]
    val workflow = testWorkflow
    for (dataset <- workflow.datasets) {
      val id = Identifier(dataset.nodeId)
      val datasetTask = mock[ProjectTask[Dataset]]
      when(datasetTask.id).thenReturn(id)
      when(project.taskOption[Dataset](dataset.task)).thenReturn(Some(datasetTask))
    }
    val sortedWorkflowNodes = workflow.topologicalSortedNodes.map(_.nodeId)
    sortedWorkflowNodes mustBe Seq(DS_A1, DS_A2, TRANSFORM_1, TRANSFORM_2, DS_B1, DS_B2, LINKS, OUTPUT, LINKING, GENERATE_OUTPUT)
  }

  it should "detect circular workflows" in {
    intercept[RuntimeException] {
      circularWorkflow.topologicalSortedNodes
    }
    intercept[RuntimeException] {
      circularWorkflow.workflowDependencyGraph
    }
  }

  it should "generate a DAG of the node dependencies" in {
    val dag = testWorkflow.workflowDependencyGraph
    dag mustBe testWorkflow.WorkflowDependencyGraph(
      startNodes = Set(
        testWorkflow.WorkflowDependencyNode(WorkflowDataset(List(), DS_A1, List(), (0, 0), DS_A1, None)),
        testWorkflow.WorkflowDependencyNode(WorkflowDataset(List(), DS_A2, List(), (0, 0), DS_A2, None))),
      endNodes = Seq(
        testWorkflow.WorkflowDependencyNode(WorkflowDataset(List(), OUTPUT, List(), (0, 0), OUTPUT, None))))
    val dsA1 = dag.startNodes.filter(_.workflowNode.nodeId == DS_A1).head
    intercept[IllegalStateException] {
      dsA1.addFollowingNode(null)
    }
    var current = dsA1
    for (nextLabel <- Seq(TRANSFORM_1, DS_B1, LINKING, LINKS, GENERATE_OUTPUT, OUTPUT)) {
      current.followingNodes.size mustBe 1
      val next = current.followingNodes.head
      next.nodeId mustBe nextLabel
      next.precedingNodes must contain(current)
      if (!next.workflowNode.isInstanceOf[WorkflowDataset]) {
        next.inputNodes must contain(current)
      }
      current = next
    }
    current mustBe dag.endNodes.head
  }

  it should "sort correctly for a workflow ending in an operator" in {
    val sortedNodes = testWorkflowEndingInOperator.topologicalSortedNodes
    sortedNodes.map(_.nodeId) mustBe Seq(DS_A, DS_B, TRANSFORM)
  }

  it should "sort by output priority" in {
    val nodes = Seq(
      dataset(DS_A, DS_A, outputPriority = None),
      dataset(DS_B, DS_B, outputPriority = Some(5.0)),
      dataset(LINKS, LINKS, outputPriority = Some(3)),
      dataset(OUTPUT, OUTPUT, outputPriority = None),
      operator(task = TRANSFORM, inputs = Seq(DS_A), outputs = Seq(DS_B), TRANSFORM, outputPriority = Some(1.5)),
      operator(task = LINKING, inputs = Seq(DS_A, DS_B), outputs = Seq(LINKS), LINKING, outputPriority = None),
      operator(task = GENERATE_OUTPUT, inputs = Seq(LINKS), outputs = Seq(OUTPUT), GENERATE_OUTPUT, outputPriority = Some(0.5))
    ).map(n => testWorkflow.WorkflowDependencyNode(n))
    testWorkflow.sortWorkflowNodesByOutputPriority(nodes).map(_.nodeId) mustBe Seq(
      GENERATE_OUTPUT, TRANSFORM, LINKS, DS_B, DS_A, LINKING, OUTPUT)
  }

  it should "build the DAG correctly for a workflow ending in an operator" in {
    val dag = testWorkflowEndingInOperator.workflowDependencyGraph
    dag.startNodes.map(_.nodeId) mustBe Set(DS_A, DS_B)
    dag.endNodes.map(_.nodeId) mustBe Seq(TRANSFORM)
  }

  it should "sort correctly for a workflow with disjunct data flows and multiple output nodes" in {
    val sortedNodes = testWorkflowWithMultipleEndNodesAndDisjunctDataFlows.topologicalSortedNodes
    sortedNodes.map(_.nodeId) mustBe Seq(DS_A, DS_B, DS_C, TRANSFORM, OP_1, OP_2)
  }

  it should "build the DAG correctly for a workflow with disjunct data flows and multiple output nodes" in {
    val dag = testWorkflowWithMultipleEndNodesAndDisjunctDataFlows.workflowDependencyGraph
    dag.startNodes.map(_.nodeId) mustBe Set(DS_A, DS_B, DS_C)
    dag.endNodes.map(_.nodeId) mustBe Seq(TRANSFORM, OP_1, OP_2)
  }

  val testWorkflow: Workflow = {
    Workflow(
      Identifier(WORKFLOW),
      operators = Seq(
        operator(task = TRANSFORM_1, inputs = Seq(DS_A1), outputs = Seq(DS_B1), TRANSFORM_1),
        operator(task = TRANSFORM_2, inputs = Seq(DS_A2), outputs = Seq(DS_B1), TRANSFORM_2),
        operator(task = LINKING, inputs = Seq(DS_B1, DS_B1), outputs = Seq(LINKS), LINKING),
        operator(task = GENERATE_OUTPUT, inputs = Seq(LINKS), outputs = Seq(OUTPUT), GENERATE_OUTPUT)
      ),
      datasets = Seq(
        dataset(DS_A1, DS_A1),
        dataset(DS_A2, DS_A2),
        dataset(DS_B, DS_B1),
        dataset(DS_B, DS_B2),
        dataset(LINKS, LINKS),
        dataset(OUTPUT, OUTPUT)
      ))
  }

  val testWorkflowEndingInOperator: Workflow = {
    Workflow(
      Identifier(WORKFLOW),
      operators = Seq(
        operator(task = TRANSFORM, inputs = Seq(DS_A, DS_B), outputs = Seq(), TRANSFORM)
      ),
      datasets = Seq(
        dataset(DS_A, DS_A),
        dataset(DS_B, DS_B)
      ))
  }

  val testWorkflowWithMultipleEndNodesAndDisjunctDataFlows: Workflow = {
    Workflow(
      Identifier(WORKFLOW),
      operators = Seq(
        operator(task = TRANSFORM, inputs = Seq(DS_A, DS_B), outputs = Seq(), TRANSFORM, outputPriority = Some(1.5)),
        operator(task = OP_1, inputs = Seq(DS_C), outputs = Seq(), OP_1),
        operator(task = OP_2, inputs = Seq(DS_C), outputs = Seq(), OP_2)
      ),
      datasets = Seq(
        dataset(DS_A, DS_A),
        dataset(DS_B, DS_B),
        dataset(DS_C, DS_C)
      ))
  }

  val circularWorkflow: Workflow = {
    Workflow(
      Identifier("circularWorkflow"),
      operators = Seq(
        operator(task = TRANSFORM_1, inputs = Seq(TRANSFORM_2), outputs = Seq(TRANSFORM_2), TRANSFORM_1),
        operator(task = TRANSFORM_2, inputs = Seq(TRANSFORM_1), outputs = Seq(TRANSFORM_1), TRANSFORM_2)
      ),
      datasets = Seq()
    )
  }

  def operator(task: String, inputs: Seq[String], outputs: Seq[String], nodeId: String, outputPriority: Option[Double] = None): WorkflowOperator = {
    WorkflowOperator(inputs = inputs, task = task, outputs = outputs, Seq(), (0, 0), nodeId, outputPriority)
  }

  def dataset(task: String, nodeId: String, outputPriority: Option[Double] = None): WorkflowDataset = {
    WorkflowDataset(Seq(), task, Seq(), (0, 0), nodeId, outputPriority)
  }
}
