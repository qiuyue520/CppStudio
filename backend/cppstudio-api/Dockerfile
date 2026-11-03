FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/cppstudio-api ./cmd/server

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/cppstudio-api /cppstudio-api
ENV CPPSTUDIO_API_ADDR=:8080 CPPSTUDIO_API_DATA_DIR=/data
EXPOSE 8080
VOLUME ["/data"]
USER nonroot:nonroot
ENTRYPOINT ["/cppstudio-api"]
