# \# ÁvilaOS

# 

# Simulador de un sistema operativo distribuido en Java. Proyecto de Sistemas Operativos (UNIMET, 2627-1).

# 

# \*\*Integrantes:\*\* Alesia Castro y Javier Aguilar.

# 

# \## Qué hace

# 

# \- Simula 2 o más computadores como instancias de una misma clase. Cada uno tiene su CPU, su RAM y su kernel.

# \- Un reloj global marca los ciclos y avisa a todos los computadores a la vez.

# \- Políticas de planificación: FCFS, EDF, Round Robin y Prioridades. Se pueden cambiar en ejecución.

# \- Procesos CPU bound, IO bound, productores y consumidores.

# \- Productor-consumidor con buffers acotados, semáforos simulados (mutex, llenos, vacíos) y latencia de red cuando el buffer está en otro computador.

# \- Modo usuario, modo sistema operativo y CPU ociosa.

# \- Métricas: utilización de CPU, throughput, tiempo de respuesta, tiempo de espera, tasa de éxito (cumplimiento de deadlines) y equidad de Jain, con gráficas.

# \- Log de eventos del kernel y de los semáforos.

# \- Guardar y cargar un escenario completo en un archivo CSV.

# 

# \## Requisitos

# 

# \- Java 21

# \- Maven

# \- NetBeans (recomendado)

# 

# \## Cómo ejecutarlo

# 

# 1\. Clonar el repositorio y abrirlo como proyecto Maven en NetBeans.

# 2\. Compilar con \*\*Clean and Build\*\*.

# 3\. Ejecutar la clase principal `AvilaOS` (paquete `avilaos`) con \*\*F6\*\*.

# 4\. En la Vista 1, configurar la simulación a mano o pulsar \*\*Cargar escenario (CSV)\*\*.

# 5\. Pulsar \*\*Iniciar simulación\*\* para abrir la Vista 2.

# 

# \## Escenarios de ejemplo

# 

# En la carpeta `csv/`:

# 

# | Archivo | Para qué sirve |

# |---|---|

# | `escenario\_ejemplo.csv` | Escenario general con 3 computadores y varias políticas |

# | `escenario\_edf\_fcfs.csv` | Compara EDF y FCFS: con FCFS el cumplimiento de deadlines es 50 %, con EDF 100 % |

# | `escenario\_latencia.csv` | Productor y consumidor en computadores distintos, para ver la latencia de red |

# | `escenario\_con\_error.csv` | Archivo con un dato inválido, para ver cómo se reporta el error |

# 

# \## Estructura

# 

# | Paquete | Contenido |

# |---|---|

# | `avilaos` | Clase principal |

# | `computador` | Computador: ensambla CPU, memoria y kernel |

# | `hardware` | Cpu y Memoria |

# | `kernel` | Kernel y la interfaz ServicioSistema |

# | `planificacion` | Políticas FCFS, EDF, Round Robin y Prioridades |

# | `sincronizacion` | Semáforo, Buffer y ColaProcesos |

# | `reloj` | RelojGlobal y EscuchaReloj |

# | `sistema` | SistemaDistribuido |

# | `modelo` | Proceso, configuración y enums |

# | `metricas` | Cálculo de las métricas |

# | `gui` | Vista 1, Vista 2 y fábrica con las validaciones |

# | `util` | ListaEnlazada, Nodo, LogEventos, GestorArchivo y GestorEscenario |

# 

# No se usan colecciones de `java.util`: las estructuras de datos son propias.

# 

# \## Control de versiones

# 

# GitFlow: `main` guarda la versión estable, `develop` integra el trabajo y cada tarea se hace en una rama propia (`feat/...`, `fix/...`) que se integra con un pull request.

# 

# \## Documentación

# 

# El informe con los diagramas UML y las conclusiones se entregó junto con el repositorio.

