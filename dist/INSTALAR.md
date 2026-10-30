# Cómo instalar Registro de Pesos GYM en tu móvil Android

Esta app no está en Google Play: se instala a mano con un fichero APK (`registro-pesos-gym-v0.1.0.apk`). Necesitas Android 8.0 o superior.

## 1. Pasar el APK al móvil

Copia `registro-pesos-gym-v0.1.0.apk` al móvil de la forma que te resulte más cómoda: cable USB (carpeta Descargas), Google Drive, correo o Telegram "Mensajes guardados".

## 2. Permitir instalar apps desconocidas

1. Abre el fichero APK desde la app **Archivos** (o desde la app donde lo descargaste).
2. Android te dirá que no puede instalar apps desconocidas desde esa aplicación. Pulsa **Ajustes**.
3. Activa **Permitir desde esta fuente** para esa app (Archivos, Drive, Chrome...).
4. Vuelve atrás.

Si tu móvil usa otro menú: Ajustes > Aplicaciones > Acceso especial > Instalar apps desconocidas.

## 3. Instalar

Pulsa **Instalar**. Si Google Play Protect avisa de que la app no es conocida, elige **Instalar de todos modos**: es una app personal firmada por su autor.

## 4. Primeros pasos

> Esta guía describe la versión de `main` (APK de prueba `registro-pesos-gym-v0.1.0+diseno-descanso.apk`). En la v0.1.0 las rutinas llevaban ejercicios y series, y cada serie se marcaba como "Hecha".

1. **Ejercicios**: la app ya trae 58 ejercicios con foto. En la pestaña Ejercicios puedes añadir los tuyos, eligiendo el grupo muscular y, si quieres, una foto de la máquina (galería o cámara).
2. **Rutinas**: en la pestaña Rutinas, crea una rutina (por ejemplo "Día 1") con un comentario opcional y añade sus grupos musculares en orden. Los ejercicios no se eligen aquí.
3. **Entrenar**: en la pestaña Entrenar, elige la rutina y pulsa **Comenzar entrenamiento**. En cada grupo, pulsa **+** y elige el ejercicio por su foto. Anota peso y repeticiones. Lo que hiciste la vez anterior aparece tenue y en cursiva como referencia; "Copiar anterior" lo convierte en los datos de hoy. El comentario de la rutina queda fijo arriba.
4. **Descanso**: al completar el peso y las repeticiones de una serie empieza solo la cuenta atrás, que aparece abajo, junto a **Terminar entrenamiento**. Puedes sumar o restar 15 s, o saltarla. Si no hay ninguna en marcha, el botón del reloj la arranca a mano. Al terminar, el móvil vibra y avisa aunque esté bloqueado. La primera vez, acepta el permiso de notificaciones. La duración y el arranque automático se cambian en **Más > Descanso entre series**.
5. Si se cierra la app a mitad, al volver te ofrecerá **reanudar** la sesión. Al acabar pulsa **Terminar entrenamiento**.
6. **Tema**: en **Más > Apariencia** eliges Sistema, Claro u Oscuro.
7. **Biblioteca de ejercicios**: importa `biblioteca-ejercicios.zip` desde **Más > Exportar / importar > Importar desde ZIP** para añadir más ejercicios. Trae las 189 ilustraciones; importarlo otra vez no duplica nada. Para pasarle a otra persona tus ejercicios y rutinas sin tu historial de pesos, usa `rutinas-y-ejercicios.zip` (se importa igual) en lugar de una exportación de la app.

## 5. Actualizar a una versión nueva

- Copia el APK nuevo al móvil e instálalo **encima** de la versión actual. Tus datos se conservan.
- Solo funciona si el APK nuevo está firmado por el mismo autor (lo estará).
- **No desinstales la app para actualizarla**: se borrarían todos tus datos. Si necesitas desinstalar, exporta antes una copia de seguridad.

## 6. Copia de seguridad y cambio de móvil

Haz una exportación de vez en cuando y siempre antes de cambiar de móvil o desinstalar:

1. Pestaña **Más** > **Exportar / importar** > **Generar copia**.
2. Pulsa **Guardar en...** (o **Compartir**) y guarda el fichero `gym-backup-AAAAMMDD-HHmm.zip` en una carpeta de Drive o en el almacenamiento y/o compártelo contigo mismo.
3. En el móvil nuevo, instala el APK, abre la app y ve a **Más** > **Exportar / importar** y pulsa **Importar desde ZIP**, elige el fichero y confirma. Verás un informe de lo importado, con tus ejercicios, rutinas, historial y fotos.

Además, Android hace una copia automática en tu cuenta de Google. Esa restauración solo funciona con el APK firmado por el mismo autor, así que no sustituye a la exportación manual.
