# ADR 0007: Relocatable Authorization Service and OpenFGA

Status: Accepted. Their current Public-zone placement is temporary. BFF, authorization domain and OpenFGA access use ports and configured base URLs. Moving them changes DNS, certificates, network policy and manifests only—not frontend code, BFF business logic or database semantics.
