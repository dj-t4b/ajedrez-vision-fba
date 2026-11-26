# Diagrama de casos de uso

GitHub dibuja este diagrama directamente (Mermaid). Los actores están a la izquierda; el sistema agrupa los casos de uso.

```mermaid
flowchart LR
    admin(["Administrador"])
    arbitro(["Árbitro"])
    jugador(["Jugador"])
    camara(["App Android<br/>(visión por computadora)"])

    subgraph sistema["Sistema de registro de partidas FBA"]
        cu1(["Iniciar sesión"])
        cu2(["Gestionar usuarios y roles"])
        cu3(["Crear partida"])
        cu4(["Capturar tablero con la cámara"])
        cu5(["Deducir jugada"])
        cu6(["Registrar jugada"])
        cu7(["Corregir jugada (deshacer)"])
        cu8(["Cargar jugada a mano"])
        cu9(["Cerrar partida con resultado"])
        cu10(["Consultar partidas y PGN"])
        cu11(["Ver panel en vivo"])
        cu12(["Generar reportes PDF / Excel"])
    end

    admin --- cu1 & cu2 & cu12 & cu11
    arbitro --- cu1 & cu3 & cu7 & cu8 & cu9 & cu12 & cu11
    jugador --- cu1 & cu10
    arbitro --- cu4
    cu4 -. include .-> cu5
    cu5 -. include .-> cu6
    camara --- cu4
    cu8 -. include .-> cu6
```

| Caso de uso | Actor | Descripción breve |
|---|---|---|
| Iniciar sesión | Todos | Email y contraseña; el sistema devuelve un token JWT con el rol. |
| Gestionar usuarios y roles | Administrador | Crear, editar, desactivar y eliminar usuarios. No se puede quitar al último administrador. |
| Crear partida | Árbitro, Administrador | Registra jugadores, torneo y ronda. La partida empieza en la posición inicial. |
| Capturar tablero / Deducir jugada | Árbitro con la app Android | La app detecta el tablero, compara la ocupación de casillas con la posición anterior y elige la jugada legal que la explica. |
| Registrar jugada | Sistema | El servidor valida la jugada contra la posición actual, calcula la notación SAN y detecta mate o tablas. |
| Corregir jugada | Árbitro | Deshace la última jugada si la cámara se equivocó. |
| Cargar jugada a mano | Árbitro | Arrastrando la pieza en el tablero de la web. |
| Cerrar partida | Árbitro | 1-0, 0-1 o ½-½ cuando hay abandono o acuerdo. |
| Consultar partidas | Todos | Lista, tablero, jugadas y PGN. |
| Generar reportes | Árbitro, Administrador | Listado en PDF o Excel y planilla PDF de cada partida. |
