/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package spic.processes.ficherossap;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.ibatis.session.SqlSession;

import spic.architecture.GenericProcess;
import spic.architecture.ProcessLogger;
import spic.architecture.exception.ProcessBusinessException;
import spic.mappers.EscrituraFechaEntregaOLMapper;
import spic.processes.utils.Utils;

/**
 *
 * @author marogase
 */
public class EscrituraFechaEntregaOL extends GenericProcess {
	
	private final String EXTENSION_FICHERO = ".txt";
	
	private final String NOMBRE_FICHERO_PROVISIONAL = "INTER2_";
	
	// Fichero que se va a generar
	private File fichero;
	
	// Flujo de escritura en fichero
	private FileWriter flujoEscritura;

	// Buffer de escritura del fichero
	private PrintWriter bufferEscritura;


	/**
	 * @param propertiesFile
	 */
	public EscrituraFechaEntregaOL(String propertiesFile) {
		super(propertiesFile);
	}

	/**
	 * @param propertiesFile
	 * @param session
	 */
	public EscrituraFechaEntregaOL(String propertiesFile, SqlSession session) {
		super(propertiesFile, session);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see spic.architecture.GenericProcess#execute()
	 */
	@Override
	protected void execute() throws ProcessBusinessException {
		ProcessLogger.calInicio("execute");
		
		try {
			ProcessLogger.traza("Creando fichero intercambio Spica-SAP");		
			escribirFechaEntregaOL();			
		}
		catch (Exception e) {
			try{
				cerrarFlujoFichero(fichero.getName(), 0);
			}catch(Exception ex){
				// No hacemos nada, si existe el fichero lo borra al producirse una excepcion, 
				//esto es por que si no, el fichero intermecio no se borra si se produce la excepcion.
			}
			ProcessLogger.error(e);
			throw new ProcessBusinessException("Se ha producido un error.");
		}
		
		ProcessLogger.calFinal("execute");
	}
	
	/**
	 * Obtiene una lista de números de pedido SAP de BBDD y la escribe en un
	 * fichero
	 * 
	 * @throws Exception
	 */
	private void escribirFechaEntregaOL() throws Exception {
		ProcessLogger.calInicio("INI EscrituraFechaEntregaOL");

		//Parametros
		String nombreArchivo = null;
		String fechaInicioPedidos = null;
		String fechaFinPedidos = null;
		String numDias = null;
		int numPedidos = 0;
		boolean error = Boolean.FALSE;
		try{
			nombreArchivo = 				getProperty("procesos.generacionPedidos.fecha_entrega_OL.sap.nombre.archivo");
		}catch(ProcessBusinessException e){
			ProcessLogger.traza("El nombre de archivo no puede ser vacío");	
			error = Boolean.TRUE;
		}
		
		try {
			fechaInicioPedidos = getProperty("procesos.generacionPedidos.fecha_entrega_OL.sap.fecha.inicio.pedidos");
		} catch(ProcessBusinessException e){
			/*No se hace nada por que puede estar a nulo en la configuracion*/
		}
		try {
			fechaFinPedidos = getProperty("procesos.generacionPedidos.fecha_entrega_OL.sap.fecha.fin.pedidos");
		} catch (ProcessBusinessException e) {
			/*No se hace nada por que puede estar a nulo en la configuracion*/
		}
		try {
			numDias = getProperty("procesos.generacionPedidos.fecha_entrega_OL.sap.num.dias");
		} catch (ProcessBusinessException e) {
		}
		try {
			String sNumPed = getProperty("procesos.generacionPedidos.fecha_entrega_OL.sap.num.pedidos");
			if (!Utils.esCadenaVacia(sNumPed) && Utils.esNumeroEntero(sNumPed)&& Integer.parseInt(sNumPed) <= 0) {
				ProcessLogger.traza("El parámetro de número de pedidos debe ser un número entero mayor de 0");
				error = Boolean.TRUE;
			}
			if (!Utils.esCadenaVacia(sNumPed)) {
					numPedidos = Integer.parseInt(sNumPed);
			}
		} catch (ProcessBusinessException e) {
		} catch (NumberFormatException e2){
			ProcessLogger.traza("El número de pedidos tiene que ser numérico");
			error = Boolean.TRUE;
		}

		// Comprobamos que el formato de la fecha inicio pedidos sea correcto
		if (!Utils.esCadenaVacia(fechaInicioPedidos) && !Utils.isFechaValida(fechaInicioPedidos)) {
			ProcessLogger.traza("El parámetro de fecha de inicio de pedidos no es correcto, el formato correcto para la fecha es DD/MM/YYYY");
			error = Boolean.TRUE;
		}
		
		// Comprobamos que el formato de la fecha fin pedidos sea correcto
		if (!Utils.esCadenaVacia(fechaFinPedidos) && !Utils.isFechaValida(fechaFinPedidos)) {
			ProcessLogger.traza("El parámetro de fecha fin de pedidos no es correcto, el formato correcto para la fecha es DD/MM/YYYY");
			error = Boolean.TRUE;
		}
		
		// Comprobamos que el número de dias y número de pedidos sea un valor numérico
		if (!Utils.esCadenaVacia(numDias) && !Utils.esNumeroEntero(numDias)) {
			ProcessLogger.traza("El parámetro de número de días no es correcto");
			error = Boolean.TRUE;
		}
		// Comprobamos que el número de días y número de pedidos no sea menor de 0
		if (!Utils.esCadenaVacia(numDias) && Utils.esNumeroEntero(numDias)&& Integer.parseInt(numDias) < 0) {
			ProcessLogger.traza("El parámetro de número de días debe ser un número entero o igual mayor de 0");
			error = Boolean.TRUE;
		}
		
		//Si no se ha producido error continuamos
		List<String> listaInsertados = new ArrayList<String>();
		int numLineasEscritas = 0;
		if (!error) {
			//Buscar elementos y terminales averiados
			ProcessLogger.traza("Se buscan los elementos en BBDD");
			
			List<HashMap<String, Object>> listaElementos = buscarElementos(fechaInicioPedidos, fechaFinPedidos, numDias);
			if (listaElementos != null && !listaElementos.isEmpty()) {
				abrirFlujoFichero();
				if (numPedidos == 0) {
					numPedidos = listaElementos.size();
				}
				//recorrer los valores obtenidos anteriormente
				//Como máximo será el valor de numPedidos distintos
				for (int i=0;i<listaElementos.size() && numLineasEscritas < numPedidos;i++) {
					//Grabar en ter_inter_ssap si no se ha insertado anteriomente
					BigDecimal ordNum = (BigDecimal) listaElementos.get(i).get("ORD_NUM");
					BigDecimal ordOrden = (BigDecimal) listaElementos.get(i).get("ORD_ORDEN");
					String numPedido = listaElementos.get(i).get("NUM_PEDIDO").toString();
					
					String codFab = listaElementos.get(i).get("COD_FAB").toString();
					if (!listaInsertados.contains(numPedido)) {
						listaInsertados.add(numPedido);
											
						ProcessLogger.traza("Se inserta la línea en el fichero de texto:" + numPedido );
						//Se generará por cada número de pedido distinto un registro en el fichero de texto
						bufferEscritura.println(numPedido);
						numLineasEscritas++;	
					} 
					ProcessLogger.traza("Se graban los elementos en tabla ter_inter_SSAP");
					grabarEnTerInterSSAP(numPedido, codFab, ordNum, ordOrden);
					
				}
				// Cerramos el fichero
				cerrarFlujoFichero(nombreArchivo,numLineasEscritas);
			}
			
			ProcessLogger.traza("Numero de líneas escritas: " + numLineasEscritas);
			
		}
		
		ProcessLogger.calFinal("FIN EscrituraFechaEntregaOL");
	} // escribirFechaEntregaOL()
	/**
	 * Marcar solicitudes enviadas
	 * @param ordNum
	 */
//	private void marcarSolicitudesEnviadas(String ordNum) throws Exception{
//		ProcessLogger.calInicio("INI marcarSolicitudesEnviadas");
//		EscrituraFechaEntregaOLMapper mapper = getSession().getMapper(EscrituraFechaEntregaOLMapper.class);
//		HashMap<String, String> datos = new HashMap<String, String>();
//		datos.put("ordNum", ordNum);
//		mapper.marcarSolicitudesComoEnviadas(datos);
//		ProcessLogger.calFinal("FIN marcarSolicitudesEnviadas");
//	}

	/**
	 * Graba en la tabla Ter_inter_SSAP
	 * @param numPedido
	 * @param codFab
	 * @throws Exception
	 */
	private void grabarEnTerInterSSAP(String numPedido, String codFab, BigDecimal ordNum, BigDecimal ordOrden ) throws Exception {
		ProcessLogger.calInicio("INI grabarEnTerInterSSAP");
		EscrituraFechaEntregaOLMapper mapper = getSession().getMapper(EscrituraFechaEntregaOLMapper.class);
		HashMap<String, Object> datos = new HashMap<String, Object>();
		datos.put("numPedido", numPedido);
		datos.put("codFab", codFab);
		datos.put("ordNum", ordNum);
		datos.put("ordOrden", ordOrden);
		BigDecimal seq = mapper.obtenerSeqTerInterSSAP(datos);
		if(seq == null){
			mapper.insertTerInterSSAP(datos);
		}else{
			mapper.updateFecTerInterSSAP(seq);
		}
		ProcessLogger.calFinal("FIN grabarEnTerInterSSAP");
	}
	/**
	 * Obtiene una lista con los numeros de pedidos de base de datos.
	 * @param fechaInicio
	 * @param fechaFin
	 * @param numDias
	 * @return List<HashMap<String, Object>>
	 * @throws Exception
	 */
	private List<HashMap<String, Object>> buscarElementos(String fechaInicio, String fechaFin, String numDias) throws Exception {
		ProcessLogger.calInicio("INI buscarElementos");
		List<HashMap<String, Object>> numElementos = null;
		EscrituraFechaEntregaOLMapper mapper = getSession().getMapper(EscrituraFechaEntregaOLMapper.class);
		HashMap<String, String> fechas = new HashMap<String, String>();
		fechas.put("fechaInicio", fechaInicio);
		fechas.put("fechaFin", fechaFin);
		fechas.put("numDias", numDias);
		numElementos = mapper.selectElementos(fechas);
		ProcessLogger.calFinal("FIN buscarElementos");
		return numElementos;
	}

	/**
	 * Abre un flujo de escritura para el fichero
	 * @throws Exception 
	 */
	private void abrirFlujoFichero() throws Exception {

		ProcessLogger.calInicio("INICIO abrirFlujoFichero");

		// Recuperamos los parámetros de base de datos
		String rutaFichero = 	getProperty("EscrituraFicheroSAP.proceso.Ruta");
		// Si no encuentra la ruta lanza una excepción
		if (!Utils.comprobarExisteFichero(rutaFichero))
			throw new IOException();


		// Añadimos el nombre del fichero provisional a la ruta y formamos el nombre del fichero con la fecha-hora y extensión
		Date myDate = new Date();
		String fechaEnvio = new SimpleDateFormat("yyyyMMddHHmmss").format(myDate);
		rutaFichero = rutaFichero.trim() + File.separatorChar + NOMBRE_FICHERO_PROVISIONAL + fechaEnvio + EXTENSION_FICHERO;
                //rutaFichero = NOMBRE_FICHERO_PROVISIONAL + fechaEnvio + EXTENSION_FICHERO;

		fichero = new File(rutaFichero);

		flujoEscritura = 	new FileWriter(fichero);
		bufferEscritura = 	new PrintWriter(flujoEscritura);

		ProcessLogger.calFinal("FIN abrirFlujoFichero");
	}
	
	/**
	 * Cierra el flujo de escritura del fichero
	 */
	private void cerrarFlujoFichero(String nombreFichero, int numLineasEscritas) {
		ProcessLogger.calInicio("Cerrando el flujo de datos del fichero");
		try {
			bufferEscritura.flush();
			flujoEscritura.flush();

			bufferEscritura.close();
			flujoEscritura.close();
			
			/* Nombre del fichero sin al extension */
			if (nombreFichero.contains(EXTENSION_FICHERO)) {
				nombreFichero = nombreFichero.substring(0, nombreFichero.lastIndexOf(EXTENSION_FICHERO));
			}
			
			/* Se renombra al fichero final si se han escrito líneas */
			
			File ficheroFinal = new File(fichero.getAbsolutePath().replace(NOMBRE_FICHERO_PROVISIONAL, nombreFichero));
			if(numLineasEscritas > 0){	
				fichero.renameTo(ficheroFinal);
			}else{
				fichero.delete();
			}
			
			ProcessLogger.traza("Flujo de datos con el fichero cerrado");
		}
		catch (IOException ioe) {
			ProcessLogger.error(ioe);
		}
		ProcessLogger.calFinal("Cerrando el flujo de datos del fichero");
	}
	
	
	public static void main(String[] args) {

		try {
			EscrituraFechaEntregaOL p ;
			if(args.length == 0){
				 p = new EscrituraFechaEntregaOL(
						 "D:/Area/Formacion/Jenkins_Octubre2016/Ejercicios/Eclipse/ficherossap/conf/properties/spicProcess.properties");
			}else{
				 p = new EscrituraFechaEntregaOL(args[0]);
			}
			p.exec();
			
		} catch (Exception e) {
                       e.printStackTrace();
		}
	}

}
