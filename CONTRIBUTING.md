# Contributing to Kafka Hands-On Guide

Thank you for your interest in contributing! This document provides guidelines for contributing to the project.

## How Can I Contribute?

### 1. Reporting Bugs

If you find a bug, please create an issue with:

- **Clear title**: Describe the issue briefly
- **Steps to reproduce**: Detailed steps to recreate the bug
- **Expected behavior**: What should happen
- **Actual behavior**: What actually happens
- **Environment**: OS, Java version, Kafka version
- **Logs**: Relevant error messages or stack traces

### 2. Suggesting Enhancements

Feature requests are welcome! Please include:

- **Use case**: Why is this feature needed?
- **Proposed solution**: How should it work?
- **Alternatives**: Other approaches you've considered
- **Examples**: Similar features in other projects

### 3. Code Contributions

#### Before You Start

1. **Check existing issues**: Someone might already be working on it
2. **Create an issue**: Discuss the change before implementing
3. **Fork the repository**: Work on your own fork
4. **Create a branch**: Use descriptive branch names

#### Development Setup

1. **Fork and clone**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/kafka-hands-on-guide.git
   cd kafka-hands-on-guide
   ```

2. **Start Kafka**:
   ```bash
   docker-compose up -d
   ```

3. **Build the project**:
   ```bash
   mvn clean install
   ```

4. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

#### Coding Guidelines

**Java Code Style:**
- Follow standard Java conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public methods
- Keep methods focused and concise
- Use dependency injection (constructor injection preferred)

**Example:**
```java
/**
 * Send a message to Kafka with retry logic.
 * 
 * @param topic The target topic
 * @param message The message to send
 * @return true if sent successfully, false otherwise
 */
public boolean sendWithRetry(String topic, Object message) {
    // Implementation
}
```

**Commit Messages:**
- Use present tense ("Add feature" not "Added feature")
- Be descriptive but concise
- Reference issue numbers when applicable

**Examples:**
```
Add transactional producer example
Fix consumer offset commit issue (#42)
Update documentation for batch processing
```

#### Code Structure

When adding new features:

**New Producer:**
1. Create class in `producer/` package
2. Add JavaDoc documentation
3. Implement the pattern
4. Add TODO comments for future enhancements
5. Add method in `KafkaService`
6. Add endpoint in `DemoController`
7. Update documentation

**New Consumer:**
1. Create class in `consumer/` package
2. Use `@KafkaListener` annotation
3. Add proper error handling
4. Log processing steps
5. Update documentation

**New Model:**
1. Create class in `model/` package
2. Extend `BaseMessage` if applicable
3. Add validation annotations
4. Include serialVersionUID
5. Add Lombok annotations

#### Testing

Before submitting:

1. **Build successfully**:
   ```bash
   mvn clean install
   ```

2. **Test manually**:
   - Start Kafka infrastructure
   - Run the application
   - Test your feature via REST API
   - Check logs for errors
   - Verify in Kafka UI

3. **TODO: Add unit tests** (future enhancement)

#### Pull Request Process

1. **Update documentation**:
   - Update README.md if needed
   - Add examples to EXAMPLES.md
   - Update FAQ.md for common questions

2. **Create pull request**:
   - Use descriptive title
   - Reference related issue
   - Describe changes in detail
   - Include screenshots if applicable

3. **PR template**:
   ```markdown
   ## Description
   Brief description of changes
   
   ## Related Issue
   Closes #123
   
   ## Changes Made
   - Added feature X
   - Fixed bug Y
   - Updated documentation
   
   ## Testing
   - [ ] Tested locally
   - [ ] Documentation updated
   - [ ] Examples added
   
   ## Screenshots
   (if applicable)
   ```

4. **Address review comments**:
   - Respond to feedback promptly
   - Make requested changes
   - Push updates to same branch

### 4. Documentation Improvements

Documentation is crucial! You can contribute by:

- Fixing typos or grammar
- Clarifying confusing sections
- Adding examples
- Creating tutorials
- Translating documentation

**Documentation files:**
- `README.md`: Project overview
- `START-HERE.md`: Quick start guide
- `EXAMPLES.md`: Code examples
- `FAQ.md`: Common questions
- `docs/*.md`: Technical documentation

### 5. Adding Examples

Examples help others learn! When adding examples:

1. **Create clear use case**: What problem does it solve?
2. **Add to EXAMPLES.md**: Include curl commands
3. **Show expected output**: Logs, responses, UI screenshots
4. **Explain the pattern**: Why use this approach?

## Project Structure

```
src/main/java/io/github/serkutyildirim/kafka/
├── config/               # Kafka configurations
├── model/                # Message models
├── producer/             # Producer implementations
├── consumer/             # Consumer implementations
├── service/              # Business logic
└── controller/           # REST endpoints

docs/                     # Technical documentation
```

## Development Workflow

1. **Create issue** → Discuss the change
2. **Fork & branch** → Work on your fork
3. **Implement** → Write code following guidelines
4. **Test** → Verify functionality
5. **Document** → Update relevant docs
6. **Pull request** → Submit for review
7. **Address feedback** → Make requested changes
8. **Merge** → Celebrate! 🎉

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive environment for everyone.

### Our Standards

**Positive behavior:**
- Being respectful and considerate
- Welcoming newcomers
- Accepting constructive criticism
- Focusing on what's best for the community

**Unacceptable behavior:**
- Harassment or discrimination
- Trolling or insulting comments
- Personal or political attacks
- Publishing others' private information

## Getting Help

Need help contributing?

- **GitHub Issues**: Ask questions via issues
- **Documentation**: Check existing docs first
- **Examples**: Look at existing code for patterns

## Recognition

Contributors will be recognized in:
- GitHub contributors list
- Future CONTRIBUTORS.md file (if created)

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

## Questions?

Feel free to create an issue with the label "question" if you need clarification on anything.

---

Thank you for contributing to Kafka Hands-On Guide! 🚀
