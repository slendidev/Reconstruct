{
  description = "My flake";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs { inherit system; };

        jdk = pkgs.javaPackages.compiler.temurin-bin.jdk-25;
        gradle = pkgs.gradle_8;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk
            (pkgs.jdt-language-server.override {
              inherit jdk;
            })
            (pkgs.kotlin-language-server.override {
              openjdk = jdk;
              inherit gradle;
            })
          ];
        };
      }
    );
}
