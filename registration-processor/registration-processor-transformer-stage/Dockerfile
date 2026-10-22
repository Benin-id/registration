FROM eclipse-temurin:21-jre-alpine

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
LABEL source="${SOURCE}" commit_hash="${COMMIT_HASH}" commit_id="${COMMIT_ID}" build_time="${BUILD_TIME}"

ARG spring_config_label
ARG active_profile
ARG spring_config_url
ARG iam_adapter_url
ARG is_glowroot
ARG artifactory_url

ENV spring_config_label_env="${spring_config_label}" \
    active_profile_env="${active_profile}" \
    spring_config_url_env="${spring_config_url}" \
    iam_adapter_url_env="${iam_adapter_url}" \
    is_glowroot_env="${is_glowroot}" \
    artifactory_url_env="${artifactory_url}" \
    work_dir=/home/mosip \
    loader_path_env=/home/mosip/additional_jars

ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1002
ARG container_user_gid=1001

RUN apk add --no-cache ca-certificates wget fontconfig ttf-dejavu \
    && addgroup -g "${container_user_gid}" "${container_user_group}" \
    && adduser -D -u "${container_user_uid}" -G "${container_user_group}" -h "/home/${container_user}" "${container_user}" \
    && mkdir -p /home/${container_user}/additional_jars /home/${container_user}/logs /home/${container_user}/Glowroot \
    && chown -R "${container_user_uid}:${container_user_gid}" "/home/${container_user}"

WORKDIR /home/${container_user}

# This must be a runnable executable JAR containing its runtime dependencies.
COPY --chown=1002:1001 ./target/registration-processor-transformer-stage-1.3.0.jar ./registration-processor-transformer-stage.jar

VOLUME ["/home/mosip/logs", "/home/mosip/Glowroot"]
USER ${container_user_uid}:${container_user_gid}

# Optional IAM adapter download; fail on download errors instead of silently starting.
CMD ["sh", "-c", "if [ -n \"$iam_adapter_url_env\" ]; then wget -q \"$iam_adapter_url_env\" -O \"$loader_path_env/kernel-auth-adapter.jar\" || exit 1; fi; exec java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Dloader.path=\"$loader_path_env\" -Dspring.cloud.config.label=\"$spring_config_label_env\" -Dspring.profiles.active=\"$active_profile_env\" -Dspring.cloud.config.uri=\"$spring_config_url_env\" -jar registration-processor-transformer-stage.jar"]
