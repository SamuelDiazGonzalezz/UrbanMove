# Scripts

Utilidades de desarrollo, datos de demostración y evidencias.

| Script | Propósito |
|---|---|
| `dev.ps1` | Envoltorio de Docker Compose y Maven para Windows |
| `seed.*` | Carga zonas, recursos y usuarios de demostración |
| `demo.*` | Ejecuta los cinco flujos de la defensa |
| `cleanup.*` | Limpia recursos locales o cloud |

## Convenciones

- Los scripts deben ser idempotentes.
- No deben contener credenciales: leen de `.env`.
- Deben imprimir un resumen de lo que hacen.
- En CI se usan las mismas rutas que en local.
