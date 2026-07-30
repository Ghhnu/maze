# MazeGen

Mod de Fabric (Minecraft 1.21.1) que genera laberintos aleatorios de piedra antigua con
el comando:

```
/generate maze <x> <y> <z>
```

Requiere nivel de permisos de operador (op nivel 2). Funciona tanto en singleplayer
(servidor integrado) como en servidores dedicados, siempre que el mod esté instalado
en el servidor.

## Qué genera

- Recinto de ~300x300 bloques (301x301 exactos por las matemáticas de la rejilla:
  100 celdas x 3 bloques + 1 de borde).
- Pasillos de 2 bloques de ancho, laberinto "perfecto" (un único camino entre dos
  puntos cualquiera, sin bucles ni callejones redundantes) generado con backtracking
  aleatorio — **cada ejecución usa una semilla distinta**, así que ningún laberinto
  sale igual.
- Paredes: mezcla aleatoria de piedra, roca (cobblestone) y roca musgosa, de 4 bloques
  de alto.
- Antorchas de pared cada pocas celdas, en las caras de pared que dan a un pasillo.
- Enredaderas colocadas de forma puntual (~14% de probabilidad por tramo de pared,
  colgando 1-3 bloques desde el techo) para dar aspecto de ruina antigua sin tapar
  las paredes por completo.
- Suelo: mezcla de roca musgosa, bloque de musgo y **calcita** (ver nota abajo).
- Techo: cristal (vidrio) transparente sobre todo el recinto.
- Suelo de la celda de entrada: hormigón/concreto rojo.
- Suelo de la celda de salida (la celda de borde más lejana de la entrada dentro del
  propio laberinto): hormigón/concreto verde.
- Se abre un hueco de 2 bloques en la pared exterior tanto en la entrada como en la
  salida para poder entrar/salir caminando desde fuera.

## Nota sobre el "musgo pálido"

El bloque *Pale Moss Block* no existe en Minecraft 1.21.1 — se añadió en la
actualización 1.21.4 (bioma Pale Garden). Como el proyecto de referencia usa 1.21.1,
se sustituyó por **Calcita** (piedra clara) para conservar el contraste "musgo oscuro
/ piedra clara" en el suelo. Si en algún momento se sube el proyecto a 1.21.4+, basta
con cambiar `Blocks.CALCITE` por `Blocks.PALE_MOSS_BLOCK` en `MazeBuilder`.

## Rendimiento

Cada laberinto son varios cientos de miles de bloques. Para no congelar el servidor,
la construcción se reparte en varios ticks (8000 bloques por tick) mediante una cola
en `MazeBuildQueue`, en vez de colocarlos todos de golpe en el mismo tick. Verás un
mensaje de progreso al lanzar el comando y otro cuando termine, con el tiempo total.

Limitación conocida: la cola es única y global — si lanzas dos `/generate maze`
seguidos, el segundo se construye después de que termine el primero, no en paralelo.

## Estructura del proyecto

Se ha calcado la estructura de build (`build.gradle`, `gradle.properties`,
`fabric.mod.json`, workflow de GitHub Actions) del proyecto TheRedWizard que serviste
de referencia, para evitar el típico lío de versiones de Yarn/Loom/Fabric API
incompatibles entre sí.
