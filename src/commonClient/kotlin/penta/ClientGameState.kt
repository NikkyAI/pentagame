package penta

import PentaMath
import PentaViz
import com.lightningkite.reacktive.property.StandardObservableProperty
import io.data2viz.geom.Point
import io.data2viz.math.Angle
import io.data2viz.math.deg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import penta.logic.Piece
import penta.logic.field.AbstractField
import penta.logic.field.CornerField
import penta.util.length

class ClientGameState : BoardState() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    //    override var updateLogPanel: (String) -> Unit = {}
    var updatePiece: (Piece) -> Unit = { piece -> }
    val multiplayerState = StandardObservableProperty<LoginState>(LoginState.Disconnected())

    fun cornerPoint(index: Int, angleDelta: Angle = 0.deg, radius: Double = PentaMath.R_): Point {
        val angle = (-45 + (index) * 90).deg + angleDelta

        return Point(
            radius * angle.cos,
            radius * angle.sin
        ) / 2 + (Point(0.5, 0.5) * PentaMath.R_)
    }

    init {
        figures.forEach(::updatePiecePos)
    }

    fun initialize(players: List<PlayerState>) {
        logger.info { "initializing with $players" }
        processMove(PentaMove.InitGame(players))
    }

    override fun processMove(move: PentaMove) {
        super.processMove(move)
    }

    fun preProcessMove(move: PentaMove) {

        when(val state = multiplayerState.value) {
            is LoginState.Playing -> {
                GlobalScope.launch(Dispatchers.Default) {
                    state.sendMove(move)
                }
            }
            else -> {
                    processMove(move)
            }
        }
        // TODO: if playing online.. send move
        // only process received moves

    }

    // TODO: move to client
    // TODO: not common code
    override fun updatePiecePos(piece: Piece/*, override: Boolean, fieldOverride: AbstractField?*/) {
        val field: AbstractField? = figurePositions[piece.id]
        updatePiecePos(piece, field)
    }

    fun updatePiecePos(piece: Piece, field: AbstractField?) {
        var pos: Point = field?.pos ?: run {
            val radius = when (piece) {
                is Piece.GrayBlocker -> {
                    logger.info { "piece: ${piece.id}" }
                    logger.info { "selected: ${selectedGrayPiece?.id}" }
                    if (selectedGrayPiece == piece) {
                        val index = players.indexOf(currentPlayer)
                        val pos = cornerPoint(index, 10.deg, radius = (PentaMath.R_ + (3 * PentaMath.s)))
                        return@run pos
                    }
                    PentaMath.inner_r * -0.2
                }
                is Piece.BlackBlocker -> {
                    if (selectedBlackPiece == piece) {
                        val index = players.indexOf(currentPlayer)
                        val pos = cornerPoint(index, (-10).deg, radius = (PentaMath.R_ + (3 * PentaMath.s)))
                        logger.info { "cornerPos: $pos" }
                        return@run pos
                    }
                    throw IllegalStateException("black piece: $piece cannot be off the board")
                }
                is Piece.Player -> PentaMath.inner_r * -0.5
                else -> throw NotImplementedError("unhandled piece type: ${piece::class}")
            }
            val angle = (piece.pentaColor.ordinal * -72.0).deg

            logger.info { "pentaColor: ${piece.pentaColor.ordinal}" }

            Point(
                radius * angle.cos,
                radius * angle.sin
            ) / 2 + (Point(0.5, 0.5) * PentaMath.R_)
        }
        if (piece is Piece.Player && field is CornerField) {
            // find all pieces on field and order them
            val pieceIds: List<String> = figurePositions.filterValues { it == field }.keys
                .sorted()
            // find index of piece on field
            val pieceNumber = pieceIds.indexOf(piece.id).toDouble()
            val angle =
                (((field.pentaColor.ordinal * -72.0) + (pieceNumber / pieceIds.size * 360.0) + 360.0) % 360.0).deg
            pos = Point(
                pos.x + (0.55) * angle.cos,
                pos.y + (0.55) * angle.sin
            )
        }
        if (piece is Piece.Player && field == null) {
            // find all pieces on field and order them
            val playerPieces = figurePositions.filterValues { it == field }.keys
                .map { id -> figures.find { it.id == id }!! }
                .filterIsInstance<Piece.Player>()
                .filter { it.pentaColor == piece.pentaColor }
                .sortedBy { it.id }
            // find index of piece on field
            val pieceNumber = playerPieces.indexOf(piece).toDouble()
            val angle =
                (((piece.pentaColor.ordinal * -72.0) + (pieceNumber / playerPieces.size * 360.0) + 360.0 + 180.0) % 360.0).deg
            pos = Point(
                pos.x + (0.55) * angle.cos,
                pos.y + (0.55) * angle.sin
            )
        }
        piece.pos = pos
        updatePiece(piece)
    }

    // TODO: clientside
    fun findPiecesAtPos(mousePos: Point) = figures.filter {
        (it.pos - mousePos).length < it.radius
    }

    fun canClickPiece(clickedPiece: Piece): Boolean {
        if (winner != null) {
            return false
        }
        if (figurePositions[clickedPiece.id] == null) {
            return false
        }
        if (
        // make sure you are not selecting black or gray
            selectedGrayPiece == null && selectedBlackPiece == null && !selectingGrayPiece
            && clickedPiece is Piece.Player && currentPlayer.id == clickedPiece.playerId
        ) {
            if (selectedPlayerPiece == null) {
                return true
            }
            if (selectedPlayerPiece == clickedPiece) {
                return true
            }
        }

        if (selectingGrayPiece
            && selectedPlayerPiece == null
            && clickedPiece is Piece.GrayBlocker
        ) {
            return true
        }

        if (selectedPlayerPiece != null && currentPlayer.id == selectedPlayerPiece!!.playerId) {
            val playerPiece = selectedPlayerPiece!!
            val sourcePos = figurePositions[playerPiece.id] ?: run {
                return false
            }
            val targetPos = figurePositions[clickedPiece.id] ?: return false
            if (sourcePos == targetPos) {
                return false
            }
            return true
        }
        return false
    }

    /**
     * click on a piece
     * @param clickedPiece game piece that was clicked on
     */
    fun clickPiece(clickedPiece: Piece) {
        if (!canClickPiece(clickedPiece)) return

        logger.info { "currentPlayer: $currentPlayer" }
        logger.info { "selected player piece: $selectedPlayerPiece" }
        logger.info { "selected black piece: $selectedBlackPiece" }
        logger.info { "selected gray piece: $selectedGrayPiece" }

        if (figurePositions[clickedPiece.id] == null) {
            logger.error { "cannot click piece off the board" }
            return
        }
        if (
        // make sure you are not selecting black or gray
            selectedGrayPiece == null && selectedBlackPiece == null && !selectingGrayPiece
            && clickedPiece is Piece.Player && currentPlayer.id == clickedPiece.playerId
        ) {
            if (selectedPlayerPiece == null) {
                logger.info { "selecting: $clickedPiece" }
                selectedPlayerPiece = clickedPiece
                PentaViz.updateBoard()
                return
            }
            if (selectedPlayerPiece == clickedPiece) {
                logger.info { "deselecting: $clickedPiece" }
                selectedPlayerPiece = null
                PentaViz.updateBoard()
                return
            }
        }

        if (selectingGrayPiece
            && selectedPlayerPiece == null
            && clickedPiece is Piece.GrayBlocker
        ) {
            logger.info { "selecting: $clickedPiece" }
            selectedGrayPiece = clickedPiece
            selectingGrayPiece = false
//            clickedPiece.position = null
            updatePiecePos(clickedPiece, null)
            PentaViz.updateBoard()
            return
        }
        if (selectedPlayerPiece != null && currentPlayer.id == selectedPlayerPiece!!.playerId) {
            val playerPiece = selectedPlayerPiece!!
            val sourceField = figurePositions[playerPiece.id] ?: run {
                logger.error { "piece if off the board already" }
                return
            }
            val targetField = figurePositions[clickedPiece.id]
            if (targetField == null) {
                logger.error { ("$clickedPiece is not on the board") }
//                selectedPlayerPiece = null
                return
            }
            if (sourceField == targetField) {
                logger.error { ("cannot move piece onto the same field as before") }
                return
            }

            if (!canMove(sourceField, targetField)) {
                logger.error { ("can not find path") }
                return
            }

            logger.info { ("moving: ${playerPiece.id} -> $targetField") }

            val move: PentaMove = when (clickedPiece) {
                is Piece.Player -> {
                    if (playerPiece.playerId == clickedPiece.playerId) {
                        PentaMove.SwapOwnPiece(
                            playerPiece = playerPiece, otherPlayerPiece = clickedPiece,
                            from = sourceField, to = targetField
                        )
                    } else {
                        // TODO   if (player is in your team) {
                        PentaMove.SwapHostilePieces(
                            playerPiece = playerPiece, otherPlayerPiece = clickedPiece,
                            from = sourceField, to = targetField
                        )
                    }
                }
                is Piece.GrayBlocker -> {
                    PentaMove.MovePlayer(
                        playerPiece = playerPiece, from = sourceField, to = targetField
                    )
                }
                is Piece.BlackBlocker -> {
                    PentaMove.MovePlayer(
                        playerPiece = playerPiece, from = sourceField, to = targetField
                    )
                }
            }
            preProcessMove(move)

            return
        }
        logger.info { ("no action on click") }
    }

    override fun updateBoard() {
        super.updateBoard()
        PentaViz.updateBoard()
    }

    fun canClickField(targetField: AbstractField): Boolean {
        if (winner != null) {
            return false
        }
        if (
            (selectedPlayerPiece == null && selectedGrayPiece == null && selectedBlackPiece == null)
            && figurePositions.none { (k, v) -> v == targetField }
        ) {
            return false
        }
        when {
            selectedPlayerPiece != null && currentPlayer.id == selectedPlayerPiece!!.playerId -> {
                val playerPiece = selectedPlayerPiece!!

                val sourcePos = figurePositions[playerPiece.id]!!
                if (sourcePos == targetField) {
                    return false
                }

                // check if targetField is empty
                if (figurePositions.values.any { it == targetField }) {
                    val pieces = figurePositions.filterValues { it == targetField }.keys
                        .map { id ->
                            figures.find { it.id == id }
                        }
                    pieces.firstOrNull() ?: return false
                    return true
                }
            }
            selectedBlackPiece != null -> {
                if (figurePositions.values.any { it == targetField }) {
                    logger.trace { ("target position not empty") }
                    return false
                }
            }
            selectedGrayPiece != null -> {
                if (figurePositions.values.any { it == targetField }) {
                    logger.trace { ("target position not empty") }
                    return false
                }
            }
            selectedPlayerPiece == null && selectedBlackPiece == null && selectedGrayPiece == null -> {
                // do not allow clicking on field when selecting piece
                return false
            }
        }
        return true
    }

    fun clickField(targetField: AbstractField) {
        if (!canClickField(targetField)) return
        logger.info { ("currentPlayer: $currentPlayer") }
        logger.info { ("selected player piece: $selectedPlayerPiece") }
        logger.info { ("selected black piece: $selectedBlackPiece") }
        logger.info { ("selected gray piece: $selectedGrayPiece") }
        val move = when {
            selectedPlayerPiece != null && currentPlayer.id == selectedPlayerPiece!!.playerId -> {
                val playerPiece = selectedPlayerPiece!!

                val sourceField = figurePositions[playerPiece.id]!!
                if (sourceField == targetField) {
                    logger.error { ("cannot move piece onto the same field as before") }
                    return
                }

                // check if targetField is empty
                if (figurePositions.values.any { it == targetField }) {
                    logger.info { ("target position not empty") }
                    // TODO: if there is only one piece on the field, click that piece instead ?
                    val pieces = figurePositions.filterValues { it == targetField }.keys
                        .map { id ->
                            figures.find { it.id == id }
                        }
                    if (pieces.size == 1) {
                        val piece = pieces.firstOrNull() ?: return
                        clickPiece(piece)
                    }
                    return
                }

                if (!canMove(sourceField, targetField)) {
                    logger.error { ("can not find path") }
                    return
                }

                logger.info { ("moving: ${playerPiece.id} -> $targetField") }

                PentaMove.MovePlayer(
                    playerPiece = playerPiece, from = sourceField, to = targetField
                )
            }
            selectedBlackPiece != null -> {
                val blackPiece = selectedBlackPiece!!

                if (figurePositions.values.any { it == targetField }) {
                    logger.error { ("target position not empty") }
                    return
                }
                val lastMove = history.last() as PentaMove.Move
                if (lastMove !is PentaMove.CanSetBlack) {
                    logger.error { ("last move cannot set black") }
                    return
                }

                PentaMove.SetBlack(
                    piece = blackPiece, from = lastMove.to, to = targetField
                )
            }
            selectedGrayPiece != null -> {
                val grayPiece = selectedGrayPiece!!

                if (figurePositions.values.any { it == targetField }) {
                    logger.error { ("target position not empty") }
                    return
                }
                val originPos = figurePositions[grayPiece.id]

                PentaMove.SetGrey(
                    piece = grayPiece, from = originPos, to = targetField
                )
            }
            else -> {
                TODO("handle else case")
            }
        }
        preProcessMove(move)
    }

    override fun resetBoard() {
//        PentaViz.gameState = this
        PentaViz.resetBoard()
    }

    // TODO: clientside
    override fun updateAllPieces() {
        figures.forEach { piece ->
            updatePiecePos(piece)
        }
    }

    // TODO: clientside
    override fun updatePiecesAtPos(field: AbstractField?) {
        figurePositions.filterValues { it == field }.keys.map { id ->
            figures.find { it.id == id }!!
        }.forEach { piece ->
            updatePiecePos(piece)
        }
    }
}