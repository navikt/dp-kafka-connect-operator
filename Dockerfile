FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-26@sha256:e565626850e5115c18bc3173ff8eff08e04e11f96e18b082ce1cd132420a18c3
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/install/*/lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.dagpenger.kafka.connect.operator.MainKt"]
