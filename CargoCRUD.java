import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CargoCRUD {
    private MongoCollection<Document> collection;
    private Scanner scanner;
    
    public CargoCRUD(MongoCollection<Document> collection, Scanner scanner) {
        this.collection = collection;
        this.scanner = scanner;
    }
    
    public void menu() {
        while (true) {
            System.out.println("\n=== CARGOS (MongoDB) ===");
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
        System.out.print("Nome: ");
        String nome = scanner.nextLine().trim();
        
        // Validação do nome
        if (nome.isEmpty()) {
            System.out.println("Erro: O nome não pode estar vazio!");
            return;
        }
        
        // Verifica se já existe cargo com mesmo nome
        Document cargoExistente = collection.find(Filters.eq("nome", nome)).first();
        if (cargoExistente != null) {
            System.out.println("Erro: Já existe um cargo com este nome!");
            return;
        }
        
        System.out.print("Descrição: ");
        String descricao = scanner.nextLine().trim();
        
        if (descricao.isEmpty()) {
            System.out.println("Erro: A descrição não pode estar vazia!");
            return;
        }
        
        System.out.print("Salário: ");
        float salario;
        try {
            salario = scanner.nextFloat();
            scanner.nextLine();
        } catch (Exception e) {
            System.out.println("Erro: Valor de salário inválido!");
            scanner.nextLine();
            return;
        }
        
        // Validação do salário
        if (salario <= 0) {
            System.out.println("Erro: O salário deve ser maior que zero!");
            return;
        }
        
        Document cargo = new Document("nome", nome)
                .append("descricao", descricao)
                .append("salario", salario);
        
        collection.insertOne(cargo);
        System.out.println("Cargo cadastrado com sucesso!");
    }
    
    private void listar() {
        long total = collection.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum cargo cadastrado.");
            return;
        }
        
        System.out.println("\n--- CARGOS CADASTRADOS ---");
        MongoCursor<Document> cursor = collection.find().iterator();
        
        try {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                
                ObjectId id = doc.getObjectId("_id");
                String nome = doc.getString("nome");
                String descricao = doc.getString("descricao");
                float salario = doc.get("salario", 0.0f);
                
                System.out.printf("ID: %s | Nome: %s | Descrição: %s | Salário: R$ %.2f\n",
                    id.toString(), nome, descricao, salario);
            }
            System.out.printf("Total: %d cargo(s)\n", total);
        } finally {
            cursor.close();
        }
    }
    
    private void atualizar() {
        Document cargo = selecionarCargo("atualizar");
        if (cargo == null) return;
        
        ObjectId id = cargo.getObjectId("_id");
        String nomeAtual = cargo.getString("nome");
        
        System.out.print("Novo nome (atual: " + nomeAtual + "): ");
        String novoNome = scanner.nextLine().trim();
        
        if (novoNome.isEmpty()) {
            System.out.println("Erro: O nome não pode estar vazio!");
            return;
        }
        
        // Verifica se o novo nome já existe (exceto para o próprio cargo)
        if (!novoNome.equals(nomeAtual)) {
            Document cargoExistente = collection.find(
                Filters.and(
                    Filters.eq("nome", novoNome),
                    Filters.ne("_id", id)
                )
            ).first();
            
            if (cargoExistente != null) {
                System.out.println("Erro: Já existe um cargo com este nome!");
                return;
            }
        }
        
        System.out.print("Nova descrição (atual: " + cargo.getString("descricao") + "): ");
        String novaDescricao = scanner.nextLine().trim();
        
        if (novaDescricao.isEmpty()) {
            System.out.println("Erro: A descrição não pode estar vazia!");
            return;
        }
        
        System.out.print("Novo salário (atual: R$ " + cargo.get("salario") + "): ");
        float novoSalario;
        try {
            novoSalario = scanner.nextFloat();
            scanner.nextLine();
        } catch (Exception e) {
            System.out.println("Erro: Valor de salário inválido!");
            scanner.nextLine();
            return;
        }
        
        if (novoSalario <= 0) {
            System.out.println("Erro: O salário deve ser maior que zero!");
            return;
        }
        
        Document updateDoc = new Document("$set", 
            new Document("nome", novoNome)
                .append("descricao", novaDescricao)
                .append("salario", novoSalario)
        );
        
        UpdateResult result = collection.updateOne(new Document("_id", id), updateDoc);
        
        if (result.getModifiedCount() > 0) {
            System.out.println("Cargo atualizado com sucesso!");
        } else {
            System.out.println("Nenhuma alteração realizada.");
        }
    }
    
    private void deletar() {
        Document cargo = selecionarCargo("deletar");
        if (cargo == null) return;
        
        ObjectId id = cargo.getObjectId("_id");
        String nome = cargo.getString("nome");
        
        // Confirmação antes de deletar
        System.out.print("Tem certeza que deseja deletar o cargo '" + nome + "'? (s/N): ");
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            DeleteResult result = collection.deleteOne(new Document("_id", id));
            
            if (result.getDeletedCount() > 0) {
                System.out.println("Cargo deletado com sucesso!");
            } else {
                System.out.println("Erro ao deletar cargo!");
            }
        } else {
            System.out.println("Operação cancelada.");
        }
    }
    
    /**
     * Método auxiliar para selecionar um cargo pelo nome
     * @param operacao Tipo de operação (atualizar, deletar)
     * @return Document do cargo selecionado ou null se cancelado
     */
    private Document selecionarCargo(String operacao) {
        System.out.print("Digite o nome ou parte do nome do cargo para buscar: ");
        String busca = scanner.nextLine().trim();
        
        if (busca.isEmpty()) {
            System.out.println("Busca vazia!");
            return null;
        }
        
        // Busca por cargos que contenham o texto digitado (case insensitive)
        List<Document> resultados = new ArrayList<>();
        collection.find(Filters.regex("nome", ".*" + busca + ".*", "i"))
                 .into(resultados);
        
        if (resultados.isEmpty()) {
            System.out.println("Nenhum cargo encontrado com: '" + busca + "'");
            return null;
        }
        
        if (resultados.size() == 1) {
            // Se encontrou apenas um, usa automaticamente
            Document cargo = resultados.get(0);
            System.out.println("Cargo selecionado: " + cargo.getString("nome"));
            return cargo;
        }
        
        // Se encontrou múltiplos, mostra lista para seleção
        System.out.println("\n--- CARGOS ENCONTRADOS ---");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. %s | Descrição: %s | Salário: R$ %.2f\n", 
                i + 1, 
                doc.getString("nome"),
                doc.getString("descricao"),
                doc.get("salario", 0.0f));
        }
        
        System.out.print("Selecione o número do cargo para " + operacao + " (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document cargoSelecionado = resultados.get(escolha - 1);
            System.out.println("Cargo selecionado: " + cargoSelecionado.getString("nome"));
            return cargoSelecionado;
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }
}