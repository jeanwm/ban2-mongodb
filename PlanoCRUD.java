import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PlanoCRUD {
    private MongoCollection<Document> planos;
    private MongoCollection<Document> beneficios;
    private Scanner scanner;

    public PlanoCRUD(MongoDatabase database, Scanner scanner) {
        this.planos = database.getCollection("planos");
        this.beneficios = database.getCollection("beneficios");
        this.scanner = scanner;
    }

    public void menu() {
        while (true) {
            System.out.println("\n=== PLANOS ===");
            System.out.println("1. Cadastrar");
            System.out.println("2. Listar");
            System.out.println("3. Atualizar");
            System.out.println("4. Deletar");
            System.out.println("5. Vincular Benefício");
            System.out.println("6. Voltar");
            System.out.print("Escolha: ");

            int opcao = scanner.nextInt();
            scanner.nextLine();

            switch (opcao) {
                case 1 -> cadastrar();
                case 2 -> listar();
                case 3 -> atualizar();
                case 4 -> deletar();
                case 5 -> vincular();
                case 6 -> { return; }
                default -> System.out.println("Opção inválida!");
            }
        }
    }

    private void cadastrar() {
        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        
        // verifica se ja existe plano com mesmo nome
        Document planoExistente = planos.find(Filters.eq("nome", nome)).first();
        if (planoExistente != null) {
            System.out.println("Erro: Já existe um plano com este nome!");
            return;
        }

        System.out.print("Descrição: ");
        String descricao = scanner.nextLine();
        
        System.out.print("Valor: ");
        float valor;
        try {
            valor = scanner.nextFloat();
            if (valor < 0) {
                System.out.println("Erro: O valor não pode ser negativo!");
                return;
            }
        } catch (Exception e) {
            System.out.println("Erro: Valor inválido!");
            scanner.nextLine();
            return;
        }
        
        System.out.print("Tempo de duração (meses): ");
        int duracao;
        try {
            duracao = scanner.nextInt();
            if (duracao <= 0) {
                System.out.println("Erro: A duração deve ser maior que zero!");
                return;
            }
        } catch (Exception e) {
            System.out.println("Erro: Duração inválida!");
            scanner.nextLine();
            return;
        }
        
        System.out.print("Tempo de fidelidade (meses): ");
        int fidelidade;
        try {
            fidelidade = scanner.nextInt();
            if (fidelidade < 0) {
                System.out.println("Erro: A fidelidade não pode ser negativa!");
                return;
            }
        } catch (Exception e) {
            System.out.println("Erro: Fidelidade inválida!");
            scanner.nextLine();
            return;
        }
        scanner.nextLine();

        Document doc = new Document()
                .append("nome", nome)
                .append("descricao", descricao)
                .append("valor", valor)
                .append("duracao", duracao)
                .append("fidelidade", fidelidade)
                .append("beneficios", new ArrayList<ObjectId>());

        planos.insertOne(doc);
        System.out.println("Plano cadastrado com sucesso!");
    }

    private void listar() {
        long total = planos.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum plano cadastrado.");
            return;
        }

        AggregateIterable<Document> result = planos.aggregate(List.of(
            new Document("$lookup", new Document()
                .append("from", "beneficios")
                .append("localField", "beneficios")
                .append("foreignField", "_id")
                .append("as", "beneficios_info")),
            new Document("$project", new Document()
                .append("nome", 1)
                .append("descricao", 1)
                .append("valor", 1)
                .append("duracao", 1)
                .append("fidelidade", 1)
                .append("beneficios", "$beneficios_info.nome"))
        ));

        System.out.println("\n--- PLANOS CADASTRADOS ---");
        for (Document doc : result) {
            List<String> beneficiosNomes = doc.getList("beneficios", String.class);
            String beneficiosStr = beneficiosNomes != null && !beneficiosNomes.isEmpty() 
                ? String.join(", ", beneficiosNomes)
                : "Nenhum benefício vinculado";
                
            System.out.printf(
                "Nome: %s | Descrição: %s | Valor: R$%.2f | Duração: %d meses | Fidelidade: %d meses | Benefícios: %s\n",
                doc.getString("nome"),
                doc.getString("descricao"),
                doc.getDouble("valor"),
                doc.getInteger("duracao"),
                doc.getInteger("fidelidade"),
                beneficiosStr
            );
        }
        System.out.printf("Total: %d plano(s)\n", total);
    }

    private void atualizar() {
        Document plano = selecionarPlano("atualizar");
        if (plano == null) return;

        String id = plano.getObjectId("_id").toHexString();
        String nomeAtual = plano.getString("nome");

        System.out.print("Novo nome (atual: " + nomeAtual + "): ");
        String novoNome = scanner.nextLine();
        if (novoNome.isEmpty()) {
            novoNome = nomeAtual;
        } else {
            // verifica se o novo nome ja existe (exceto para o proprio plano)
            Document planoExistente = planos.find(
                Filters.and(
                    Filters.eq("nome", novoNome),
                    Filters.ne("_id", plano.getObjectId("_id"))
                )
            ).first();
            
            if (planoExistente != null) {
                System.out.println("Erro: Já existe um plano com este nome!");
                return;
            }
        }

        System.out.print("Nova descrição (atual: " + plano.getString("descricao") + "): ");
        String novaDescricao = scanner.nextLine();
        if (novaDescricao.isEmpty()) {
            novaDescricao = plano.getString("descricao");
        }

        System.out.print("Novo valor (atual: " + plano.getDouble("valor") + "): ");
        String valorInput = scanner.nextLine();
        float novoValor;
        if (valorInput.isEmpty()) {
            novoValor = plano.getDouble("valor").floatValue();
        } else {
            try {
                novoValor = Float.parseFloat(valorInput);
                if (novoValor < 0) {
                    System.out.println("Erro: O valor não pode ser negativo!");
                    return;
                }
            } catch (Exception e) {
                System.out.println("Erro: Valor inválido!");
                return;
            }
        }

        System.out.print("Nova duração (atual: " + plano.getInteger("duracao") + "): ");
        String duracaoInput = scanner.nextLine();
        int novaDuracao;
        if (duracaoInput.isEmpty()) {
            novaDuracao = plano.getInteger("duracao");
        } else {
            try {
                novaDuracao = Integer.parseInt(duracaoInput);
                if (novaDuracao <= 0) {
                    System.out.println("Erro: A duração deve ser maior que zero!");
                    return;
                }
            } catch (Exception e) {
                System.out.println("Erro: Duração inválida!");
                return;
            }
        }

        System.out.print("Nova fidelidade (atual: " + plano.getInteger("fidelidade") + "): ");
        String fidelidadeInput = scanner.nextLine();
        int novaFidelidade;
        if (fidelidadeInput.isEmpty()) {
            novaFidelidade = plano.getInteger("fidelidade");
        } else {
            try {
                novaFidelidade = Integer.parseInt(fidelidadeInput);
                if (novaFidelidade < 0) {
                    System.out.println("Erro: A fidelidade não pode ser negativa!");
                    return;
                }
            } catch (Exception e) {
                System.out.println("Erro: Fidelidade inválida!");
                return;
            }
        }

        planos.updateOne(
            Filters.eq("_id", plano.getObjectId("_id")),
            Updates.combine(
                Updates.set("nome", novoNome),
                Updates.set("descricao", novaDescricao),
                Updates.set("valor", novoValor),
                Updates.set("duracao", novaDuracao),
                Updates.set("fidelidade", novaFidelidade)
            )
        );

        System.out.println("Plano atualizado com sucesso!");
    }

    private void deletar() {
        Document plano = selecionarPlano("deletar");
        if (plano == null) return;

        String nomePlano = plano.getString("nome");
        
        System.out.print("Tem certeza que deseja deletar o plano \"" + nomePlano + "\"? (s/N): ");
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            planos.deleteOne(Filters.eq("_id", plano.getObjectId("_id")));
            System.out.println("Plano deletado com sucesso!");
        } else {
            System.out.println("Operação cancelada.");
        }
    }

    private void vincular() {
        // verifica se existem beneficios
        if (beneficios.countDocuments() == 0) {
            System.out.println("Nenhum benefício cadastrado no sistema.");
            return;
        }

        Document plano = selecionarPlano("vincular benefício");
        if (plano == null) return;

        Document beneficio = selecionarBeneficio();
        if (beneficio == null) return;

        String nomeBeneficio = beneficio.getString("nome");
        List<String> beneficiosAtuais = plano.getList("beneficios", String.class);

        // verifica se o beneficio ja esta vinculado
        if (beneficiosAtuais != null && beneficiosAtuais.contains(nomeBeneficio)) {
            System.out.println("Este benefício já está vinculado ao plano!");
            return;
        }

        planos.updateOne(
            Filters.eq("_id", plano.getObjectId("_id")),
            Updates.push("beneficios", beneficio.getObjectId("_id"))
        );

        System.out.println("Benefício '" + nomeBeneficio + "' vinculado com sucesso ao plano '" + plano.getString("nome") + "'!");
    }

    /**
     * metodo auxiliar para selecionar um plano pelo nome
     */
    private Document selecionarPlano(String operacao) {
        if (planos.countDocuments() == 0) {
            System.out.println("Nenhum plano cadastrado.");
            return null;
        }

        System.out.print("Digite o nome ou parte do nome do plano para buscar: ");
        String busca = scanner.nextLine().trim();

        if (busca.isEmpty()) {
            System.out.println("Busca não pode estar vazia!");
            return null;
        }

        // busca por planos que contenham o texto digitado no nome
        List<Document> resultados = new ArrayList<>();
        planos.find(Filters.regex("nome", ".*" + busca + ".*", "i"))
              .into(resultados);

        if (resultados.isEmpty()) {
            System.out.println("Nenhum plano encontrado com: \"" + busca + "\"");
            return null;
        }

        if (resultados.size() == 1) {
            // se encontrou apenas um, usa automaticamente
            Document plano = resultados.get(0);
            System.out.println("Plano selecionado: " + plano.getString("nome"));
            return plano;
        }

        // se encontrou multiplos, mostra lista para seleção
        System.out.println("\n--- PLANOS ENCONTRADOS ---");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. %s - %s (R$%.2f)\n", 
                i + 1, 
                doc.getString("nome"),
                doc.getString("descricao"),
                doc.getDouble("valor"));
        }

        System.out.print("Selecione o plano para " + operacao + " (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document planoSelecionado = resultados.get(escolha - 1);
            System.out.println("Plano selecionado: " + planoSelecionado.getString("nome"));
            return planoSelecionado;
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    /**
     * metodo auxiliar para selecionar um beneficio pelo nome
     */
    private Document selecionarBeneficio() {
        if (beneficios.countDocuments() == 0) {
            System.out.println("Nenhum benefício cadastrado.");
            return null;
        }

        System.out.print("Digite o nome ou parte do nome do benefício para buscar: ");
        String busca = scanner.nextLine().trim();

        if (busca.isEmpty()) {
            System.out.println("Busca não pode estar vazia!");
            return null;
        }

        // busca por beneficios que contenham o texto digitado no nome
        List<Document> resultados = new ArrayList<>();
        beneficios.find(Filters.regex("nome", ".*" + busca + ".*", "i"))
                  .into(resultados);

        if (resultados.isEmpty()) {
            System.out.println("Nenhum benefício encontrado com: \"" + busca + "\"");
            return null;
        }

        if (resultados.size() == 1) {
            // se encontrou apenas um, usa automaticamente
            Document beneficio = resultados.get(0);
            System.out.println("Benefício selecionado: " + beneficio.getString("nome"));
            return beneficio;
        }

        // se encontrou multiplos, mostra lista para seleção
        System.out.println("\n--- BENEFÍCIOS ENCONTRADOS ---");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. Nome: %s | Descrição: %s\n",
                i + 1,
                doc.getString("nome"),
                doc.getString("descricao"));
        }

        System.out.print("Selecione o benefício (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document beneficioSelecionado = resultados.get(escolha - 1);
            System.out.println("Benefício selecionado: " + beneficioSelecionado.getString("nome"));
            return beneficioSelecionado;
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }
}