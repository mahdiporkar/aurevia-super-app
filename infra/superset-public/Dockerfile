FROM apache/superset:5.0.0 AS superset-assets

FROM nginx:1.29.1-alpine
COPY --from=superset-assets /app/superset/static /usr/share/nginx/html/static
COPY --from=superset-assets /app/.venv/lib/python3.10/site-packages/flask_appbuilder/static/appbuilder /usr/share/nginx/html/static/appbuilder
COPY infra/superset-public/nginx.conf /etc/nginx/conf.d/default.conf
