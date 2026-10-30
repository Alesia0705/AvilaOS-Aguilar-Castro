package modelo;

/**
 * Clase modelo para empaquetar la configuración individual de cada computador.
 */
public class ConfiguracionComputador {
    private int idPC;
    private int memoriaRAM;
    private String politicaPlanificacion;

    public ConfiguracionComputador(int idPC, int memoriaRAM, String politicaPlanificacion) {
        this.idPC = idPC;
        this.memoriaRAM = memoriaRAM;
        this.politicaPlanificacion = politicaPlanificacion;
    }

    public int getIdPC() { 
        return idPC; 
    }
    
    public int getMemoriaRAM() { 
        return memoriaRAM; 
    }
    
    public String getPoliticaPlanificacion() { 
        return politicaPlanificacion; 
    }

    @Override
    public String toString() {
        return "PC " + idPC + " [RAM=" + memoriaRAM + "MB, Política=" + politicaPlanificacion + "]";
    }
}