# Control de Mobs (Forge 1.20.1) - v1.0.0

Panel para controlar, mob por mob (vanilla y de mods): si spawnea, en que dimensiones y a que altura, y que dropea.

## Uso
1. Con permisos de operador (o trucos activados), escribe `/mobcontrol`.
2. Izquierda: lista de **todos** los mobs (con buscador). Rojo = spawn desactivado, amarillo = tiene reglas.
3. Elige un mob y usa las pestañas:
   - **Spawn**: boton Activado/Desactivado. "Restablecer" borra todas las reglas de ese mob.
   - **Dimensiones**: lista de **todas** las dimensiones del servidor (vanilla y de mods). Clic en una:
     Permitido/Bloqueado, Y minima y Y maxima (vacio = sin limite) y "Aplicar".
     Una dimension sin regla = el mob puede aparecer ahi.
   - **Drops**: "Drops originales" (mantener/quitar) y lista de drops extra. Para agregar uno:
     clic en el espacio, elige el item a la derecha (pestaña **Todos** o **Mi inventario** para items custom),
     pon cantidad min/max y probabilidad (1-100 %) y pulsa "Agregar drop".
4. Todo se guarda al instante en el mundo.

## Notas
- El bloqueo de spawn cubre spawn natural, spawners, estructuras, patrullas y refuerzos. No bloquea huevos, comandos,
  dispensadores ni crianza (para que puedas seguir probando).
- La altura se compara con la del punto de aparicion.
- Los drops extra se suman a los originales (o los reemplazan si quitas los originales). No cambian la experiencia.
- Los mobs de mods aparecen en la lista si registran atributos (todos los mobs normales lo hacen).
- Debe estar instalado en cliente y servidor.

## Compilar con GitHub
1. Sube todo el contenido de esta carpeta (incluida `.github`) a un repositorio.
2. Pestaña **Actions** -> "Compilar mod" (corre solo con cada push, o "Run workflow").
3. Descarga el artefacto **mobcontrol-jar** (`mobcontrol-1.0.0.jar`) y ponlo en `mods`.

Local: JDK 17 + Gradle 8.1.1 -> `gradle build` (el .jar queda en `build/libs/`).
