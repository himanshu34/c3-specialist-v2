package co.nayan.canvas

abstract class JudgmentCanvasFragment(layoutID: Int) : CanvasFragment(layoutID) {

    protected var currentCardPosition = 0

    protected fun submitJudgement(judgment: Boolean) {
        canvasViewModel.submitJudgement(judgment)
        canvasViewModel.processNextRecord()
        canvasViewModel.setupUndoRecordState()
    }

    protected fun undoJudgment() {
        undo(currentCardPosition - 1)
        canvasViewModel.undoJudgment()
        canvasViewModel.setupUndoRecordState()
    }
}