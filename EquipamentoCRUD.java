import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class EquipamentoCRUD {
    private MongoCollection<Document> collection;
    private MongoDatabase database;
    private Scanner scanner;

    public EquipamentoCRUD(MongoDatabase database, Scanner scanner) {
        this.database = database;
        this.collection = database.getCollection("equipamentos");
        this.scanner = scanner;
    }

    public void menu() {
        while (true) {
            System.out.println("\n=== EQUIPAMENTOS ===");
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
        System.out.print("Modelo: ");
        String modelo = scanner.nextLine().trim();
        
        if (modelo.isEmpty()) {
            System.out.println("Erro: O modelo não pode estar vazio!");
            return;
        }

        // verifica se ja existe equipamento com mesmo modelo
        Document equipamentoExistente = collection.find(Filters.eq("modelo", modelo)).first();
        if (equipamentoExistente != null) {
            System.out.println("Erro: Já existe um equipamento com este modelo!");
            return;
        }

        System.out.print("Valor: ");
        if (!scanner.hasNextFloat()) {
            System.out.println("Erro: Valor deve ser um número!");
            scanner.nextLine();
            return;
        }
        float valor = scanner.nextFloat();
        
        if (valor <= 0) {
            System.out.println("Erro: O valor deve ser maior que zero!");
            scanner.nextLine();
            return;
        }

        System.out.print("Status (0=Inativo, 1=Ativo): ");
        if (!scanner.hasNextInt()) {
            System.out.println("Erro: Status deve ser um número inteiro!");
            scanner.nextLine();
            return;
        }
        int status = scanner.nextInt();
        scanner.nextLine();

        if (status != 0 && status != 1) {
            System.out.println("Erro: Status deve ser 0 (Inativo) ou 1 (Ativo)!");
            return;
        }

        Document doc = new Document("modelo", modelo)
                .append("valor", valor)
                .append("status", status);

        collection.insertOne(doc);
        System.out.println("Equipamento cadastrado com sucesso!");
    }

    private void listar() {
        long total = collection.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum equipamento cadastrado.");
            return;
        }

        System.out.println("\n--- EQUIPAMENTOS CADASTRADOS ---");
        for (Document doc : collection.find()) {
            String statusStr = doc.getInteger("status") == 1 ? "Ativo" : "Inativo";
            System.out.printf("ID: %s | Modelo: %s | Valor: R$%.2f | Status: %s\n",
                doc.getObjectId("_id").toHexString(),
                doc.getString("modelo"),
                doc.getDouble("valor"),
                statusStr
            );
        }
        System.out.printf("Total: %d equipamento(s)\n", total);
    }

    private void atualizar() {
        Document equipamento = selecionarEquipamento("atualizar");
        if (equipamento == null) return;

        String modeloAtual = equipamento.getString("modelo");
        ObjectId id = equipamento.getObjectId("_id");

        System.out.print("Novo modelo (atual: " + modeloAtual + "): ");
        String novoModelo = scanner.nextLine().trim();
        
        if (novoModelo.isEmpty()) {
            System.out.println("Erro: O modelo não pode estar vazio!");
            return;
        }

        // verifica se o novo modelo ja existe em outro equipamento
        if (!novoModelo.equals(modeloAtual)) {
            Document equipamentoExistente = collection.find(
                Filters.and(
                    Filters.eq("modelo", novoModelo),
                    Filters.ne("_id", id)
                )
            ).first();
            
            if (equipamentoExistente != null) {
                System.out.println("Erro: Já existe outro equipamento com este modelo!");
                return;
            }
        }

        System.out.print("Novo valor (atual: " + equipamento.getDouble("valor") + "): ");
        if (!scanner.hasNextFloat()) {
            System.out.println("Erro: Valor deve ser um número!");
            scanner.nextLine();
            return;
        }
        float novoValor = scanner.nextFloat();
        
        if (novoValor <= 0) {
            System.out.println("Erro: O valor deve ser maior que zero!");
            scanner.nextLine();
            return;
        }

        System.out.print("Novo status (0=Inativo, 1=Ativo) (atual: " + equipamento.getInteger("status") + "): ");
        if (!scanner.hasNextInt()) {
            System.out.println("Erro: Status deve ser um número inteiro!");
            scanner.nextLine();
            return;
        }
        int novoStatus = scanner.nextInt();
        scanner.nextLine();

        if (novoStatus != 0 && novoStatus != 1) {
            System.out.println("Erro: Status deve ser 0 (Inativo) ou 1 (Ativo)!");
            return;
        }

        var update = Updates.combine(
                Updates.set("modelo", novoModelo),
                Updates.set("valor", novoValor),
                Updates.set("status", novoStatus)
        );

        var result = collection.updateOne(Filters.eq("_id", id), update);

        if (result.getModifiedCount() > 0) {
            System.out.println("Equipamento atualizado com sucesso!");
        } else {
            System.out.println("Nenhuma alteração foi realizada.");
        }
    }

    private void deletar() {
        Document equipamento = selecionarEquipamento("deletar");
        if (equipamento == null) return;

        String modelo = equipamento.getString("modelo");
        ObjectId id = equipamento.getObjectId("_id");

        System.out.print("Tem certeza que deseja deletar o equipamento '" + modelo + "'? (s/N): ");
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            // verifica se o equipamento esta vinculado a manutencoes
            MongoCollection<Document> manutencoes = database.getCollection("manutencoes");
            Document manutencaoVinculada = manutencoes.find(Filters.eq("id_equipamento", id)).first();
            
            if (manutencaoVinculada != null) {
                System.out.println("Erro: Não é possível deletar este equipamento pois ele está vinculado a uma ou mais manutenções!");
                return;
            }

            var result = collection.deleteOne(Filters.eq("_id", id));

            if (result.getDeletedCount() > 0) {
                System.out.println("Equipamento deletado com sucesso!");
            } else {
                System.out.println("Erro ao deletar equipamento!");
            }
        } else {
            System.out.println("Operação cancelada.");
        }
    }

    /**
     * metodo auxiliar para selecionar um equipamento pelo modelo
     * @param operacao tipo de operacao (atualizar, deletar)
     * @return Document do equipamento selecionado ou null se cancelado
     */
    private Document selecionarEquipamento(String operacao) {
        System.out.print("Digite o modelo ou parte do modelo para buscar: ");
        String busca = scanner.nextLine().trim();

        if (busca.isEmpty()) {
            // se busca vazia, lista todos os equipamentos
            List<Document> todosEquipamentos = new ArrayList<>();
            collection.find().into(todosEquipamentos);
            
            if (todosEquipamentos.isEmpty()) {
                System.out.println("Nenhum equipamento cadastrado.");
                return null;
            }
            
            return selecionarDaLista(todosEquipamentos, operacao);
        }

        // busca por modelos que contenham o texto digitado (case insensitive)
        List<Document> resultados = new ArrayList<>();
        collection.find(Filters.regex("modelo", ".*" + busca + ".*", "i"))
                 .into(resultados);

        if (resultados.isEmpty()) {
            System.out.println("Nenhum equipamento encontrado com: " + busca);
            return null;
        }

        return selecionarDaLista(resultados, operacao);
    }

    /**
     * metodo auxiliar para selecionar um equipamento de uma lista
     * @param equipamentos lista de equipamentos encontrados
     * @param operacao tipo de operacao
     * @return Document do equipamento selecionado ou null se cancelado
     */
    private Document selecionarDaLista(List<Document> equipamentos, String operacao) {
        if (equipamentos.size() == 1) {
            // se encontrou apenas um, usa automaticamente
            Document equipamento = equipamentos.get(0);
            String statusStr = equipamento.getInteger("status") == 1 ? "Ativo" : "Inativo";
            System.out.println("Equipamento selecionado: " + equipamento.getString("modelo") + 
                             " | Valor: R$" + equipamento.getDouble("valor") + 
                             " | Status: " + statusStr);
            return equipamento;
        }

        // se encontrou multiplos, mostra lista para selecao
        System.out.println("\n--- EQUIPAMENTOS ENCONTRADOS ---");
        for (int i = 0; i < equipamentos.size(); i++) {
            Document doc = equipamentos.get(i);
            String statusStr = doc.getInteger("status") == 1 ? "Ativo" : "Inativo";
            System.out.printf("%d. Modelo: %s | Valor: R$%.2f | Status: %s\n", 
                i + 1, 
                doc.getString("modelo"),
                doc.getDouble("valor"),
                statusStr);
        }

        System.out.print("Selecione o número do equipamento para " + operacao + " (1-" + equipamentos.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > equipamentos.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document equipamentoSelecionado = equipamentos.get(escolha - 1);
            String statusStr = equipamentoSelecionado.getInteger("status") == 1 ? "Ativo" : "Inativo";
            System.out.println("Equipamento selecionado: " + equipamentoSelecionado.getString("modelo") + 
                             " | Valor: R$" + equipamentoSelecionado.getDouble("valor") + 
                             " | Status: " + statusStr);
            return equipamentoSelecionado;
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }
}