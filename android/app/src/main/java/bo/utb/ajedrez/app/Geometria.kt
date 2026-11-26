package bo.utb.ajedrez.app

import kotlin.math.hypot

/** Cálculos geométricos puros (sin Android ni OpenCV) para poder probarlos en la JVM. */
object Geometria {

    data class P(val x: Double, val y: Double)

    /** Lado del tablero enderezado más cercano a la cámara (a8 arriba a la izquierda). */
    enum class Lado { ARRIBA, DERECHA, ABAJO, IZQUIERDA }

    /**
     * Con la cámara inclinada, el borde del tablero más cercano se ve más largo en la imagen.
     * @param esquinas en orden a8, h8, h1, a1 (coordenadas de la imagen)
     */
    fun ladoCercano(esquinas: List<P>): Lado {
        val (a8, h8, h1, a1) = esquinas
        val largos = mapOf(
            Lado.ARRIBA to dist(a8, h8),
            Lado.DERECHA to dist(h8, h1),
            Lado.ABAJO to dist(h1, a1),
            Lado.IZQUIERDA to dist(a1, a8),
        )
        return largos.maxBy { it.value }.key
    }

    /**
     * Zona de cada casilla (fracciones x0, y0, x1, y1 de la casilla enderezada) donde apoya la base
     * de la pieza: la franja hacia la cámara. La silueta de una pieza alta se recuesta hacia el
     * lado opuesto y tapa la casilla de atrás, pero su base queda en esta franja.
     */
    fun zonaBase(lado: Lado): DoubleArray = when (lado) {
        Lado.ABAJO -> doubleArrayOf(0.2, 0.45, 0.8, 0.9)
        Lado.ARRIBA -> doubleArrayOf(0.2, 0.1, 0.8, 0.55)
        Lado.IZQUIERDA -> doubleArrayOf(0.1, 0.2, 0.55, 0.8)
        Lado.DERECHA -> doubleArrayOf(0.45, 0.2, 0.9, 0.8)
    }

    /**
     * Ancho del marco a cada lado del tablero enderezado: busca los márgenes (a, b) con los que 9 líneas
     * igualmente espaciadas entre a y largo-b caen sobre los bordes de las casillas.
     * @param perfil fuerza de borde acumulada en cada posición (columna o fila) del tablero enderezado
     * @return margen inicial y final, en píxeles
     */
    fun ajustarLineas(perfil: DoubleArray, maxMargen: Int): Pair<Int, Int> {
        val n = perfil.size
        // suavizado triangular: tolera líneas a 1-2 px pero mantiene el máximo en la línea exacta
        val p = DoubleArray(n) { i -> (-2..2).sumOf { d -> if (i + d in 0 until n) perfil[i + d] * (3 - Math.abs(d)) else 0.0 } }
        var mejor = Triple(0, 0, Double.NEGATIVE_INFINITY)
        for (a in 0 until maxMargen) for (b in 0 until maxMargen) {
            val paso = (n - 1 - a - b) / 8.0
            val puntaje = (0..8).sumOf { k -> p[Math.round(a + k * paso).toInt()] }
            if (puntaje > mejor.third) mejor = Triple(a, b, puntaje)
        }
        return mejor.first to mejor.second
    }

    /** Ordena 4 puntos como arriba-izq, arriba-der, abajo-der, abajo-izq. */
    fun ordenar(ps: List<P>): List<P> = listOf(
        ps.minBy { it.x + it.y }, ps.minBy { it.y - it.x }, ps.maxBy { it.x + it.y }, ps.maxBy { it.y - it.x },
    )

    /*
     * Detección automática en la posición inicial. Se trabaja sobre un tablero enderezado "candidato"
     * de 8x8 casillas (índice = fila * 8 + columna); las filas van en la dirección de las filas del
     * ajedrez (1..8), pero todavía no se sabe de qué lado están las blancas ni dónde está la columna a.
     */

    /**
     * Qué tan bien encaja el candidato con la posición inicial: las filas 0, 1, 6 y 7 tienen piezas
     * (mucha textura, desviación alta) y las 4 del centro están vacías (lisas).
     * @param desviacion desviación estándar de gris de cada casilla
     */
    fun puntajeInicial(desviacion: DoubleArray): Double {
        fun fila(f: Int) = (0 until 8).sumOf { desviacion[f * 8 + it] } / 8
        return (fila(0) + fila(1) + fila(6) + fila(7)) / 4 - (fila(2) + fila(3) + fila(4) + fila(5)) / 4
    }

    /**
     * Ubica a8, h8, h1, a1 entre las esquinas del candidato (0 = arriba-izq, 1 = arriba-der,
     * 2 = abajo-der, 3 = abajo-izq) en la posición inicial:
     * - las blancas están del lado de las filas más claras (piezas blancas);
     * - a1 es la esquina oscura de ese lado, y el color de cada esquina sale de la paridad de las
     *   32 casillas vacías del centro (a1 y h8 son casillas oscuras).
     * @param medias gris medio de cada casilla
     * @return índices de esquina del candidato para a8, h8, h1, a1
     */
    fun orientar(medias: DoubleArray): IntArray {
        fun fila(f: Int) = (0 until 8).sumOf { medias[f * 8 + it] } / 8
        val blancasAbajo = fila(6) + fila(7) > fila(0) + fila(1)
        var pares = 0.0
        var impares = 0.0
        for (f in 2..5) for (c in 0..7) if ((f + c) % 2 == 0) pares += medias[f * 8 + c] else impares += medias[f * 8 + c]
        // (fila + columna) par incluye las esquinas 0 (0,0) y 2 (7,7); si esas casillas son claras, las oscuras son 1 y 3
        val oscuras = if (pares > impares) listOf(1, 3) else listOf(0, 2)
        val ladoBlancas = if (blancasAbajo) listOf(2, 3) else listOf(0, 1)
        val a1 = oscuras.first { it in ladoBlancas }
        val h1 = ladoBlancas.first { it != a1 }
        val a8 = vecinaVertical(a1)
        val h8 = vecinaVertical(h1)
        return intArrayOf(a8, h8, h1, a1)
    }

    /** Esquina del otro extremo de la misma columna: 0↔3, 1↔2. */
    private fun vecinaVertical(e: Int) = 3 - e

    private fun dist(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)
}
