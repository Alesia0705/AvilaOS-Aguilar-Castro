# ÁvilaOS

Simulador de sistema operativo en Java (Swing) para el curso de Sistemas Operativos de la UNIMET (2627-1).

**Integrantes:** Alesia Castro y Javier Aguilar.

El Proyecto 1 simula CPU, memoria, planificación de procesos, semáforos y métricas. El **Proyecto 2** agrega un disco simulado con sistema de archivos, planificación de disco y suspensión de procesos.

## Qué hace (Proyecto 2)

- **Disco simulado** de 100 a 500 bloques con asignación encadenada, área de intercambio (swap) configurable, cabezal con posición, dirección y velocidad configurable, y una única cola de E/S. Cada bloque leído o escrito cuesta un ciclo de reloj.
- **Sistema de archivos** en árbol (`JTree`): nombre, bloques, dueño, permisos y primer bloque. Tabla de asignación (`JTable`) con la cadena de bloques de cada archivo.
- **Operaciones de archivo (crear, leer, actualizar, eliminar) solo mediante procesos de E/S** que pasan por las colas normales de la CPU y se bloquean con el motivo `ESPERA_DISCO`.
- **Cuatro políticas de disco** intercambiables en caliente: FIFO, SSTF, SCAN y C-SCAN.
- **Modelo de siete estados** y **planificador de mediano plazo**: suspende procesos bloqueados hacia el swap cuando falta RAM y los reactiva después.
- **Modos administrador y usuario**: el usuario solo lee archivos propios o públicos, solo crea archivos dentro de sus directorios y no modifica archivos del sistema ni de otros. La interfaz valida y explica los rechazos sin detener la simulación.
- **Métricas**: desplazamiento total del cabezal, tiempo de espera promedio en la cola de E/S, solicitudes atendidas por política, suspensiones y reactivaciones, gráfica de desplazamiento acumulado, además de las métricas del Proyecto 1.
- **Log de eventos** con las decisiones del disco y del planificador de mediano plazo.
- **Persistencia**: carga de procesos y del sistema de archivos desde JSON, y guardado del sistema de archivos en JSON desde la pestaña «Disco y archivos».

## Requisitos

- Java 21
- Maven
- NetBeans (recomendado)

## Cómo ejecutarlo

1. Clonar el repositorio y abrir la carpeta `AvilaOS` como proyecto Maven en NetBeans.
2. Compilar con **Clean and Build**.
3. Ejecutar la clase principal `AvilaOS` (paquete `avilaos`) con **F6**.
4. En la Vista 1 pulsar **Cargar procesos (JSON)** y **Cargar sistema de archivos (JSON)** (ambos son obligatorios), elegir la política de disco y el modo iniciales y pulsar **Iniciar simulación**.
5. La Vista 2 muestra las colas del computador (con el motivo de cada bloqueo), la pestaña «Disco y archivos», las métricas, las gráficas y el log.

Desde la línea de comandos: `mvn -f AvilaOS/pom.xml compile exec:java`.

## Archivos JSON de ejemplo (carpeta `json/`)

| Archivo | Para qué sirve |
|---|---|
| `procesos_enunciado.json` | Los 8 procesos del caso del enunciado |
| `sistema_archivos_enunciado.json` | Sistema de archivos del enunciado (disco de 200 bloques, cabezal en 50) |
| `procesos_swap.json` | RAM de 48: obliga a suspender y reactivar procesos |
| `sistema_archivos_ejemplo.json` | Ejemplo en el formato anterior (también se puede cargar) |

Formato de procesos: `globalConfig` (`cycleDurationMs`, `swapSizeBlocks`, `initialDiskPolicy`), `computer` (`ramCapacity`, `policy`, `quantum`) y `processes` (`name`, `memory`, `deadline`, `instructions`, `targetPath`, `operation`, `fileSizeBlocks`).
Formato del sistema de archivos: `diskConfig` (`totalBlocks`, `initialHead`) y `fileSystem` (`path`, `type`, `startBlock`, `blocks`, `owner`, `permissions`).

## Pruebas por consola

Se ejecutan desde NetBeans con clic derecho, **Run File**, con la carpeta `AvilaOS` como directorio de trabajo:

| Clase | Qué comprueba |
|---|---|
| `disco.PruebaDiscoAnexo` | Asignación encadenada, swap y desplazamiento del cabezal |
| `disco.PruebaPoliticasDisco` | Compara FIFO, SSTF, SCAN y C-SCAN con los mismos procesos |
| `filesystem.PruebaFilesystemJson` | Carga y guardado del sistema de archivos y permisos básicos |
| `kernel.PruebaMedianoPlazo` | Suspensiones y reactivaciones con RAM insuficiente |
| `kernel.PruebaModosUsuario` | Reglas de permisos y simulación completa como usuario |

## Estructura

`modelo` (proceso y enums), `hardware`, `kernel`, `planificacion` (CPU), `disco` (disco y políticas de disco), `filesystem`, `sistema`, `reloj`, `sincronizacion`, `util` (listas propias, log, gestores de JSON), `metricas` y `gui`. La interfaz solo observa el sistema y le envía órdenes; la lógica no vive en las ventanas. El código propio no usa colecciones de `java.util`.

## Flujo de trabajo en GitHub

Rama `main` estable, rama `develop` de integración y ramas por funcionalidad (`feat/...`, `test/...`) con Pull Requests revisados hacia `develop`. El trabajo está repartido en Issues.

## Informe

El informe en PDF (clases, enums, interfaces, dos diagramas UML y conclusiones sobre las políticas de disco y la suspensión) se entrega junto con este repositorio.