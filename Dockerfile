FROM openjdk:8

# Define additional package sources.
RUN ["/bin/bash", "-c", "set -o pipefail \
 && curl -sL https://deb.nodesource.com/setup_8.x | bash - \
 && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - \
 && echo \"deb https://dl.yarnpkg.com/debian/ stable main\" | tee /etc/apt/sources.list.d/yarn.list \
 && echo \"deb https://dl.bintray.com/sbt/debian /\" | tee -a /etc/apt/sources.list.d/sbt.list \
 && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 \
"]

# Install required packages.
RUN apt update && apt install -y --no-install-recommends \
    # Platform tools:
    apt-utils \
    build-essential \
    # Package managers:
    python-pip \
    python-setuptools \
    python-wheel \
    nodejs \
    sbt \
    yarn=1.3.2-1 \
    # Version Control Systems:
    cvs \
    git \
    mercurial \
    subversion \
    # ScanCode dependencies:
    bzip2 \
    libxml2-dev \
    libxslt1-dev \
    python-dev \
    xz-utils \
    zlib1g \
    # Workaround for broken libmagic vendored in ScanCode:
    libmagic1 \
    libmagic-mgc \
 # Install package manager specifics.
 && npm install -g npm@5.5.1 \
 && pip install virtualenv==15.1.0 pipdeptree==0.10.1 \
 # Install git-repo.
 && curl https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo \
 && chmod a+x /usr/local/bin/repo \
 # Install ScanCode.
 && curl -sL https://github.com/nexB/scancode-toolkit/releases/download/v2.2.1/scancode-toolkit-2.2.1.tar.bz2 | tar -C /opt -jx \
 && cp /usr/share/file/magic.mgc /opt/scancode-toolkit-2.2.1/src/typecode/data/magic/noarch/magic.mgc \
 && cp /usr/lib/x86_64-linux-gnu/libmagic.so.1.0.0 /opt/scancode-toolkit-2.2.1/src/typecode/bin/linux-64/lib/libmagic.so \
 # Clean up the apt cache to reduce the image size.
 && apt -y autoremove \
 && apt -y clean \
 && rm -rf /var/lib/apt/lists /var/cache/apt/archives \
 # Create a new non-root user.
 && groupadd -r toolkit && useradd -g toolkit -l -m -r toolkit \
 && /opt/scancode-toolkit-2.2.1/scancode --version \
 && chown -R toolkit:toolkit /opt/scancode-toolkit-2.2.1

# Copy the OSS Review Toolkit binaries to the container.
ENV APPDIR=/opt/oss-review-toolkit
ADD ./*/build/distributions/*.tar "${APPDIR}/"
WORKDIR "${APPDIR}"

# Add the tools to the PATH environment.
ENV PATH="${APPDIR}/analyzer/bin:${APPDIR}/downloader/bin:${APPDIR}/graph/bin:${APPDIR}/scanner/bin:/opt/scancode-toolkit-2.2.1/bin:${PATH}"

# Change to the newly created user.
USER toolkit

CMD ["/bin/bash"]
