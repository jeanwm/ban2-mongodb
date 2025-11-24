import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class BeneficioCRUD {
    private MongoCollection<Document> collection;
    private Scanner scanner;
    
    public BeneficioCRUD(MongoCollection<Document> collection, Scanner scanner) {
        this.collection = collection;
        this.scanner = scanner;
    }
    
    public void menu() {
        while (true) {
            System.out.println("\n=== BENEFÍCIOS (MongoDB) ===");
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
        
        // Verifica se já existe um benefício com o mesmo nome
        Document beneficioExistente = collection.find(Filters.eq("nome", nome)).first();
        if (beneficioExistente != null) {
            System.out.println("Erro: Já existe um benefício com este nome!");
            return;
        }
        
        System.out.print("Descrição: ");
        String descricao = scanner.nextLine().trim();
        
        // Validação da descrição
        if (descricao.isEmpty()) {
            System.out.println("Erro: A descrição não pode estar vazia!");
            return;
        }
        
        Document beneficio = new Document("nome", nome)
                .append("descricao", descricao);
        
        collection.insertOne(beneficio);
        System.out.println("Benefício cadastrado com sucesso!");
    }
    
    private void listar() {
        long total = collection.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum benefício cadastrado.");
            return;
        }
        
        System.out.println("\n--- BENEFÍCIOS CADASTRADOS ---");
        MongoCursor<Document> cursor = collection.find().iterator();
        int contador = 0;
        
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            ObjectId id = doc.getObjectId("_id");
            String nome = doc.getString("nome");
            String descricao = doc.getString("descricao");
            
            System.out.printf("ID: %s | Nome: %s | Descrição: %s\n",
                id.toString(), nome, descricao);
            contador++;
        }
        cursor.close();
        
        System.out.printf("Total: %d benefício(s)\n", contador);
    }
    
    private void atualizar() {
        Document beneficio = selecionarBeneficio("atualizar");
        if (beneficio == null) return;
        
        ObjectId id = beneficio.getObjectId("_id");
        String nomeAtual = beneficio.getString("nome");
        
        System.out.print("Novo nome (atual: " + nomeAtual + "): ");
        String novoNome = scanner.nextLine().trim();
        
        // Validação do novo nome
        if (novoNome.isEmpty()) {
            System.out.println("Erro: O nome não pode estar vazio!");
            return;
        }
        
        // Verifica se o novo nome já existe (exceto para o próprio benefício)
        if (!novoNome.equals(nomeAtual)) {
            Document beneficioExistente = collection.find(
                Filters.and(
                    Filters.eq("nome", novoNome),
                    Filters.ne("_id", id)
                )
            ).first();
            
            if (beneficioExistente != null) {
                System.out.println("Erro: Já existe outro benefício com este nome!");
                return;
            }
        }
        
        System.out.print("Nova descrição (atual: " + beneficio.getString("descricao") + "): ");
        String novaDescricao = scanner.nextLine().trim();
        
        // Validação da nova descrição
        if (novaDescricao.isEmpty()) {
            System.out.println("Erro: A descrição não pode estar vazia!");
            return;
        }
        
        Document updateDoc = new Document("$set", 
            new Document("nome", novoNome)
                .append("descricao", novaDescricao));
        
        UpdateResult result = collection.updateOne(new Document("_id", id), updateDoc);
        
        if (result.getModifiedCount() > 0) {
            System.out.println("Benefício atualizado com sucesso!");
        } else {
            System.out.println("Nenhuma alteração realizada.");
        }
    }
    
    private void deletar() {
        Document beneficio = selecionarBeneficio("deletar");
        if (beneficio == null) return;
        
        ObjectId id = beneficio.getObjectId("_id");
        String nome = beneficio.getString("nome");
        
        // Confirmação antes de deletar
        System.out.print("Tem certeza que deseja deletar o benefício '" + nome + "'? (s/N): ");
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            DeleteResult result = collection.deleteOne(new Document("_id", id));
            
            if (result.getDeletedCount() > 0) {
                System.out.println("Benefício deletado com sucesso!");
            } else {
                System.out.println("Erro ao deletar benefício!");
            }
        } else {
            System.out.println("Operação cancelada.");
        }
    }
    
    /**
     * Método auxiliar para selecionar um benefício por nome
     * @param operacao Tipo de operação (atualizar, deletar)
     * @return Document do benefício selecionado ou null se cancelado
     */
    private Document selecionarBeneficio(String operacao) {
        long total = collection.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum benefício cadastrado.");
            return null;
        }
        
        // Se há poucos benefícios, mostra lista completa
        if (total <= 10) {
            System.out.println("\n--- LISTA DE BENEFÍCIOS ---");
            List<Document> todosBeneficios = new ArrayList<>();
            collection.find().into(todosBeneficios);
            
            for (int i = 0; i < todosBeneficios.size(); i++) {
                Document doc = todosBeneficios.get(i);
                System.out.printf("%d. %s - %s\n", 
                    i + 1, 
                    doc.getString("nome"), 
                    doc.getString("descricao"));
            }
            
            System.out.print("Selecione o número do benefício para " + operacao + " (1-" + todosBeneficios.size() + "): ");
            try {
                int escolha = scanner.nextInt();
                scanner.nextLine();
                
                if (escolha < 1 || escolha > todosBeneficios.size()) {
                    System.out.println("Seleção inválida!");
                    return null;
                }
                
                Document beneficioSelecionado = todosBeneficios.get(escolha - 1);
                System.out.println("Benefício selecionado: " + beneficioSelecionado.getString("nome"));
                return beneficioSelecionado;
                
            } catch (Exception e) {
                System.out.println("Entrada inválida!");
                scanner.nextLine();
                return null;
            }
        }
        
        // Para muitos benefícios, usa busca por nome
        System.out.print("Digite o nome ou parte do nome para buscar: ");
        String busca = scanner.nextLine().trim();
        
        if (busca.isEmpty()) {
            System.out.println("Busca vazia! Mostrando todos os benefícios:");
            return selecionarBeneficio(operacao); // Recursão para mostrar lista completa
        }
        
        // Busca por benefícios que contenham o texto no nome
        List<Document> resultados = new ArrayList<>();
        collection.find(Filters.regex("nome", ".*" + busca + ".*", "i"))
                 .into(resultados);
        
        if (resultados.isEmpty()) {
            System.out.println("Nenhum benefício encontrado com: '" + busca + "'");
            return null;
        }
        
        if (resultados.size() == 1) {
            // Se encontrou apenas um, usa automaticamente
            Document beneficio = resultados.get(0);
            System.out.println("Benefício selecionado: " + beneficio.getString("nome"));
            return beneficio;
        }
        
        // Se encontrou múltiplos, mostra lista para seleção
        System.out.println("\n--- BENEFÍCIOS ENCONTRADOS ---");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. %s - %s\n", 
                i + 1, 
                doc.getString("nome"), 
                doc.getString("descricao"));
        }
        
        System.out.print("Selecione o número do benefício para " + operacao + " (1-" + resultados.size() + "): ");
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