import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TelefoneCRUD {
    private MongoCollection<Document> telefones;
    private Scanner scanner;

    public TelefoneCRUD(MongoDatabase database, Scanner scanner) {
        this.telefones = database.getCollection("telefones");
        this.scanner = scanner;
    }

    public void menu() {
        while (true) {
            System.out.println("\n=== TELEFONES ===");
            System.out.println("1. Cadastrar");
            System.out.println("2. Listar");
            System.out.println("3. Atualizar");
            System.out.println("4. Deletar");
            System.out.println("5. Voltar");
            System.out.print("Escolha: ");

            int opcao = scanner.nextInt();
            scanner.nextLine();

            switch (opcao) {
                case 1 -> cadastrar();
                case 2 -> listar();
                case 3 -> atualizar();
                case 4 -> deletar();
                case 5 -> { return; }
                default -> System.out.println("Opção inválida!");
            }
        }
    }

    private void cadastrar() {
        System.out.print("Número: ");
        String numero = scanner.nextLine();

        // Validação básica do número
        if (!numero.matches("\\d+")) {
            System.out.println("Erro: O número deve conter apenas dígitos!");
            return;
        }

        // Verifica se o número já existe
        Document telefoneExistente = telefones.find(Filters.eq("numero", numero)).first();
        if (telefoneExistente != null) {
            System.out.println("Erro: Este número já está cadastrado!");
            return;
        }

        Document doc = new Document()
                .append("numero", numero);

        telefones.insertOne(doc);
        System.out.println("Telefone cadastrado com sucesso!");
    }

    private void listar() {
        long total = telefones.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum telefone cadastrado.");
            return;
        }

        System.out.println("\n--- TELEFONES CADASTRADOS ---");
        for (Document doc : telefones.find()) {
            System.out.printf("ID: %s | Número: %s\n",
                doc.getObjectId("_id").toHexString(),
                doc.getString("numero")
            );
        }
        System.out.printf("Total: %d telefone(s)\n", total);
    }

    private void atualizar() {
        String numeroAtual = selecionarTelefone("atualizar");
        if (numeroAtual == null) return;

        System.out.print("Novo número: ");
        String novoNumero = scanner.nextLine();

        // Validação do novo número
        if (!novoNumero.matches("\\d+")) {
            System.out.println("Erro: O número deve conter apenas dígitos!");
            return;
        }

        // Verifica se o novo número já existe (exceto para o próprio telefone)
        Document telefoneExistente = telefones.find(
            Filters.and(
                Filters.eq("numero", novoNumero),
                Filters.ne("numero", numeroAtual)
            )
        ).first();
        
        if (telefoneExistente != null) {
            System.out.println("Erro: Este número já está cadastrado em outro telefone!");
            return;
        }

        telefones.updateOne(
            Filters.eq("numero", numeroAtual),
            Updates.set("numero", novoNumero)
        );

        System.out.println("Telefone atualizado com sucesso!");
    }

    private void deletar() {
        String numero = selecionarTelefone("deletar");
        if (numero == null) return;

        // Confirmação antes de deletar
        System.out.print("Tem certeza que deseja deletar o telefone " + numero + "? (s/n): ");
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            telefones.deleteOne(Filters.eq("numero", numero));
            System.out.println("Telefone deletado com sucesso!");
        } else {
            System.out.println("Operação cancelada.");
        }
    }

    /**
     * Método auxiliar para selecionar um telefone pelo número
     * @param operacao Tipo de operação (atualizar, deletar)
     * @return Número do telefone selecionado ou null se cancelado
     */
    private String selecionarTelefone(String operacao) {
        System.out.print("Digite o número ou parte do número para buscar: ");
        String busca = scanner.nextLine();

        // Busca por números que contenham o texto digitado
        List<Document> resultados = new ArrayList<>();
        telefones.find(Filters.regex("numero", ".*" + busca + ".*"))
                 .into(resultados);

        if (resultados.isEmpty()) {
            System.out.println("Nenhum telefone encontrado com: " + busca);
            return null;
        }

        if (resultados.size() == 1) {
            // Se encontrou apenas um, usa automaticamente
            String numero = resultados.get(0).getString("numero");
            System.out.println("Telefone selecionado: " + numero);
            return numero;
        }

        // Se encontrou múltiplos, mostra lista para seleção
        System.out.println("\n--- TELEFONES ENCONTRADOS ---");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. Número: %s\n", i + 1, doc.getString("numero"));
        }

        System.out.print("Selecione o número do telefone para " + operacao + " (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            String numeroSelecionado = resultados.get(escolha - 1).getString("numero");
            System.out.println("Telefone selecionado: " + numeroSelecionado);
            return numeroSelecionado;
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }
}