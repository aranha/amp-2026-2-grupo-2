package br.pucminas.aed.credito.domain.model;

/** CPF validado por dígitos verificadores; não identifica clienteId do legado. */
public record CPF(String numero) {
    public CPF {
        if (numero == null) throw new IllegalArgumentException("CPF é obrigatório");
        numero = numero.replaceAll("[.\\-]", "");
        if (!numero.matches("\\d{11}") || numero.chars().distinct().count() == 1 ||
                digito(numero, 9) != numero.charAt(9) - '0' ||
                digito(numero, 10) != numero.charAt(10) - '0') {
            throw new IllegalArgumentException("CPF inválido");
        }
    }
    private static int digito(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
